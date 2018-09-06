package io.axoniq.axonhub.localstorage.file;

import io.axoniq.axondb.Event;
import io.axoniq.axonhub.exception.ErrorCode;
import io.axoniq.axonhub.exception.MessagingPlatformException;
import io.axoniq.axonhub.localstorage.EventInformation;
import io.axoniq.axonhub.localstorage.EventTypeContext;
import io.axoniq.axonhub.localstorage.StorageCallback;
import io.axoniq.axonhub.localstorage.transaction.PreparedTransaction;
import io.axoniq.axonhub.localstorage.transformation.EventTransformer;
import io.axoniq.axonhub.localstorage.transformation.EventTransformerFactory;
import io.axoniq.axonhub.localstorage.transformation.ProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Author: marc
 */
public class PrimaryEventStore extends SegmentBasedEventStore {
    private static final Logger logger = LoggerFactory.getLogger(PrimaryEventStore.class);

    private final EventTransformerFactory eventTransformerFactory;
    private final Synchronizer synchronizer;
    private final AtomicReference<WritePosition> writePositionRef = new AtomicReference<>();
    private final AtomicLong lastToken = new AtomicLong(-1);
    private final ConcurrentNavigableMap<Long, Map<String, SortedSet<PositionInfo>>> positionsPerSegmentMap = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicLong> sequenceNumbersPerAggregate = new ConcurrentHashMap<>();
    private final Map<Long, ByteBufferEventSource> readBuffers = new ConcurrentHashMap<>();
    private EventTransformer eventTransformer;

    public PrimaryEventStore(EventTypeContext context, IndexManager indexCreator, EventTransformerFactory eventTransformerFactory, StorageProperties storageProperties) {
        super(context, indexCreator, storageProperties);
        this.eventTransformerFactory = eventTransformerFactory;
        synchronizer = new Synchronizer(context, storageProperties, this::completeSegment);
    }

    @Override
    public void init(long lastInitialized) {
        File storageDir  = new File(storageProperties.getStorage(context));
        FileUtils.checkCreateDirectory(storageDir);
        eventTransformer = eventTransformerFactory.get(VERSION, storageProperties.getFlags(), storageProperties);
        initLatestSegment(lastInitialized, Long.MAX_VALUE, storageDir);
    }

    private void initLatestSegment(long lastInitialized, long nextToken, File storageDir) {
        long first = getFirstFile(lastInitialized, storageDir);
        WritableEventSource buffer = getOrOpenDatafile(first);
        FileUtils.delete(storageProperties.index(context, first));
        FileUtils.delete(storageProperties.bloomFilter(context, first));
        sequenceNumbersPerAggregate.clear();
        long sequence = first;
        EventByteBufferIterator iterator = new EventByteBufferIterator(buffer, first, first);
        Map<String, SortedSet<PositionInfo>> aggregatePositions = new ConcurrentHashMap<>();
        positionsPerSegmentMap.put(first, aggregatePositions);
        while( sequence < nextToken && iterator.hasNext()) {
            EventInformation event = iterator.next();
            if( isDomainEvent(event.getEvent()) ) {
                aggregatePositions.computeIfAbsent(event.getEvent().getAggregateIdentifier(),
                                                   k -> new ConcurrentSkipListSet<>())
                                  .add(new PositionInfo(event.getPosition(),
                                                        event.getEvent().getAggregateSequenceNumber()));

                sequenceNumbersPerAggregate.computeIfAbsent(event.getEvent().getAggregateIdentifier(),
                                                            k -> new AtomicLong()).set(event.getEvent()
                                                                                            .getAggregateSequenceNumber());
            }
            sequence++;
        }
        List<EventInformation> pendingEvents = iterator.pendingEvents();
        if( ! pendingEvents.isEmpty()) {
            logger.warn("Failed to position to transaction {}, {} events left in transaction, moving to end of transaction", nextToken, pendingEvents.size());
            for(EventInformation event : pendingEvents) {
                if( isDomainEvent(event.getEvent()) ) {
                    aggregatePositions.computeIfAbsent(event.getEvent().getAggregateIdentifier(),
                                                       k -> new ConcurrentSkipListSet<>())
                                      .add(new PositionInfo(event.getPosition(),
                                                            event.getEvent().getAggregateSequenceNumber()));

                    sequenceNumbersPerAggregate.computeIfAbsent(event.getEvent().getAggregateIdentifier(),
                                                                k -> new AtomicLong()).set(event.getEvent()
                                                                                                .getAggregateSequenceNumber());
                }
                sequence++;
            }
        }
        lastToken.set(sequence-1);

        buffer.putInt(buffer.position(), 0);
        WritePosition writePosition = new WritePosition(sequence, buffer.position(), buffer, first);
        writePositionRef.set(writePosition);
        synchronizer.init(writePosition);

        if( next != null) {
            next.init(first);
        }
    }

    private long getFirstFile(long lastInitialized, File events) {
        String[] eventFiles = FileUtils.getFilesWithSuffix(events, storageProperties.getEventsSuffix());

        return Arrays.stream(eventFiles)
                     .map(name -> Long.valueOf(name.substring(0, name.indexOf('.'))))
                     .filter(segment -> segment < lastInitialized)
                     .max(Long::compareTo)
                     .orElse(0L);
    }


    @Override
    public FilePreparedTransaction prepareTransaction(List<Event> origEventList) {
        List<ProcessedEvent>eventList = eventTransformer.transform(origEventList);
        int eventSize = eventBlockSize(eventList);
        WritePosition writePosition = claim(eventSize, eventList.size());
        return new FilePreparedTransaction(writePosition, eventSize, eventList);
    }

    @Override
    public void store(PreparedTransaction basePreparedTransaction, StorageCallback storageCallback) {
        try {
            FilePreparedTransaction preparedTransaction = (FilePreparedTransaction)basePreparedTransaction;
            List<ProcessedEvent> eventList = preparedTransaction.getEventList();
            int eventSize = preparedTransaction.getEventSize();
            WritePosition writePosition = preparedTransaction.getWritePosition();
            synchronizer.register(writePosition, new StorageCallback() {
                private final AtomicBoolean execute = new AtomicBoolean(true);

                @Override
                public boolean onCompleted(long firstToken) {
                    if( execute.getAndSet(false)) {
                        storageCallback.onCompleted(firstToken);
                        lastToken.set(firstToken + eventList.size() -1);
                        return true;
                    }
                    return false;
                }

                @Override
                public void onError(Throwable cause) {
                    storageCallback.onError(cause);
                }
            });
            write(writePosition, eventSize, eventList);
            synchronizer.notifyWritePositions();
        } catch (RuntimeException cause) {
            storageCallback.onError(cause);
        }
    }

    @Override
    protected void handover(Long segment, Runnable callback) {
        callback.run();
    }

    @Override
    public void cleanup() {
        synchronizer.shutdown();
        readBuffers.forEach((s, source) -> source.clean(5));
        if( next != null) next.cleanup();
    }

    @Override
    protected SortedSet<Long> getSegments() {
        return positionsPerSegmentMap.descendingKeySet();
    }

    @Override
    protected Optional<EventSource> getEventSource(long segment) {
        if( readBuffers.containsKey(segment) ) {
            return Optional.of(readBuffers.get(segment).duplicate());
        }
        return Optional.empty();
    }

    @Override
    protected SortedSet<PositionInfo> getPositions(long segment, String aggregateId) {
        return positionsPerSegmentMap.get(segment).get(aggregateId);
    }

    @Override
    public long getLastToken() {
        return lastToken.get();
    }

    @Override
    public boolean reserveSequenceNumbers(List<Event> events) {
        Map<String, MinMaxPair> minMaxPerAggregate = new HashMap<>();
        events.stream()
              .filter(this::isDomainEvent)
              .forEach(e -> minMaxPerAggregate.computeIfAbsent(e.getAggregateIdentifier(), i -> new MinMaxPair(e.getAggregateSequenceNumber())).setMax(e.getAggregateSequenceNumber()));

        for( Map.Entry<String,MinMaxPair> entry : minMaxPerAggregate.entrySet()) {
            AtomicLong current = sequenceNumbersPerAggregate.computeIfAbsent(entry.getKey(),
                                                                             id -> new AtomicLong(
                                                                                     getLastSequenceNumber(id, 10).orElse(-1L)));

            if( ! current.compareAndSet(entry.getValue().getMin() - 1, entry.getValue().getMax())) {
                logger.info("Invalid sequence number for {}, expected {} found {}", entry.getKey(), entry.getValue().min -1,
                            current.get());
                return false;
            }
        }
        return true;
    }

    @Override
    public Stream<String> getBackupFilenames(long lastSegmentBackedUp) {
        return next!= null ? next.getBackupFilenames(lastSegmentBackedUp): Stream.empty();
    }

    @Override
    public void rollback( long token) {
        if( token >= getLastToken()) {
            return;
        }
        synchronizer.shutdown();

        for( long segment: getSegments()) {
            if( segment > token) {
                removeSegment(segment);
            }
        }

        if( positionsPerSegmentMap.isEmpty() && next != null) {
            next.rollback(token);
        }

        initLatestSegment(Long.MAX_VALUE, token+1, new File(storageProperties.getStorage(context)));
    }

    private void removeSegment(long segment) {
        positionsPerSegmentMap.remove(segment);
        ByteBufferEventSource eventSource = readBuffers.remove(segment);
        if( eventSource != null) eventSource.clean(0);
        FileUtils.delete(storageProperties.dataFile(context, segment));
    }

    private void completeSegment(WritePosition writePosition) {
        try {
            indexManager.createIndex(writePosition.segment, positionsPerSegmentMap.get(writePosition.segment), false);
        } catch( RuntimeException re) {
            logger.warn("Failed to create index", re);
        }
        if( next != null) {
            next.handover(writePosition.segment, () -> {
                positionsPerSegmentMap.remove(writePosition.segment);
                sequenceNumbersPerAggregate.clear();
                ByteBufferEventSource source = readBuffers.remove(writePosition.segment);
                logger.debug("Handed over {}, remaining segments: {}", writePosition.segment, positionsPerSegmentMap.keySet());
                source.clean(storageProperties.getPrimaryCleanupDelay());
            });
        }
    }

    private void write(WritePosition writePosition, int eventSize, List<ProcessedEvent> eventList) {
        ByteBuffer writeBuffer = writePosition.buffer.duplicate().getBuffer();
        writeBuffer.position(writePosition.position);
        writeBuffer.putInt(0);
        writeBuffer.put(VERSION);
        writeBuffer.putShort((short) eventList.size());
        Checksum checksum = new Checksum();
        int eventsPosition = writeBuffer.position();
        for( ProcessedEvent event : eventList) {
            int position = writeBuffer.position();
            writeBuffer.putInt(event.getSerializedSize());
            writeBuffer.put(event.toByteArray());
            if( event.isDomainEvent()) {
                positionsPerSegmentMap.get(writePosition.segment).computeIfAbsent(event.getAggregateIdentifier(),
                                                                                  k -> new ConcurrentSkipListSet<>())
                                      .add(new PositionInfo(position, event.getAggregateSequenceNumber()));
            }
        }

        writeBuffer.putInt(checksum.update(writeBuffer, eventsPosition, writeBuffer.position() - eventsPosition).get());
        writeBuffer.position(writePosition.position);
        writeBuffer.putInt(eventSize);
    }

    private WritePosition claim(int eventBlockSize, int nrOfEvents)  {
        int totalSize = HEADER_BYTES + eventBlockSize + TX_CHECKSUM_BYTES;
        if( totalSize > storageProperties.getSegmentSize()-9)
            throw new MessagingPlatformException(ErrorCode.PAYLOAD_TOO_LARGE, "Size of transaction too large, max size = " + (storageProperties.getSegmentSize() - 9));
        WritePosition writePosition;
        do {
            writePosition = writePositionRef.getAndAccumulate(
                    new WritePosition(nrOfEvents, totalSize),
                    (prev, x) -> prev.incrementedWith(x.sequence, x.position));

            if (writePosition.isOverflow(totalSize)) {
                // only one thread can be here
                logger.debug("{}: Creating new segment {}", context, writePosition.sequence);

                writePosition.buffer.putInt(writePosition.position, -1);

                WritableEventSource buffer = getOrOpenDatafile(writePosition.sequence);
                writePositionRef.set(writePosition.reset(buffer));
            }
        } while (!writePosition.isWritable(totalSize));

        return writePosition;
    }

    private WritableEventSource getOrOpenDatafile(long segment)  {
        File file= storageProperties.dataFile(context, segment);
        long size = storageProperties.getSegmentSize();
        if( file.exists()) {
            size = file.length();
        }
        try(FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel()) {
            positionsPerSegmentMap.computeIfAbsent(segment, k -> new ConcurrentHashMap<>());
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            buffer.put(VERSION);
            buffer.putInt(storageProperties.getFlags());
            WritableEventSource writableEventSource = new WritableEventSource(buffer, eventTransformer);
            readBuffers.put(segment, writableEventSource);
            return writableEventSource;
        } catch (IOException ioException) {
            throw new MessagingPlatformException(ErrorCode.DATAFILE_READ_ERROR, "Failed to open segment: " + segment, ioException);
        }
    }

    private int eventBlockSize(List<ProcessedEvent> eventList) {
        int size = 0;
        for( ProcessedEvent event : eventList) {
            size += 4 + event.getSerializedSize();
        }
        return size;
    }



    private class MinMaxPair {

        private final long min;
        private volatile long max;

        public MinMaxPair(long min) {
            this.min = min;
            this.max = min-1;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }

        public void setMax(long max) {
            if( max != this.max + 1) throw new IndexOutOfBoundsException();
            this.max = max;
        }
    }
}
