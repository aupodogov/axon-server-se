package io.axoniq.axonhub.localstorage.file;

import io.axoniq.axondb.Event;
import io.axoniq.axonhub.exception.ErrorCode;
import io.axoniq.axonhub.exception.MessagingPlatformException;
import io.axoniq.axonhub.localstorage.EventInformation;
import io.axoniq.axonhub.localstorage.EventTypeContext;
import io.axoniq.axonhub.localstorage.transaction.PreparedTransaction;
import io.axoniq.axonhub.localstorage.transformation.EventTransformerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Author: marc
 */
public class SecondaryEventStore extends SegmentBasedEventStore {
    private final ScheduledExecutorService scheduledExecutorService;
    private final SortedSet<Long> segments = new ConcurrentSkipListSet<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<Long, WeakReference<ByteBufferEventSource>> lruMap = new ConcurrentSkipListMap<>();
    private final EventTransformerFactory eventTransformerFactory;


    public SecondaryEventStore(EventTypeContext context, IndexManager indexManager,
                               EventTransformerFactory eventTransformerFactory,
                               StorageProperties storageProperties) {
        super(context, indexManager, storageProperties);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory(context + "-file-cleanup-"));
        this.eventTransformerFactory = eventTransformerFactory;
    }


    @Override
    public void init(long lastInitialized)  {
        File events  = new File(storageProperties.getStorage(context));
        FileUtils.checkCreateDirectory(events);
        String[] eventFiles = FileUtils.getFilesWithSuffix(events, storageProperties.getEventsSuffix());
        Arrays.stream(eventFiles)
                           .map(name -> Long.valueOf(name.substring(0, name.indexOf('.'))))
                           .filter(segment -> segment < lastInitialized)
                           .forEach(segments::add);

        long firstValidIndex = segments.stream().filter(this::indexValid).findFirst().orElse(-1L);
        logger.debug("First valid index: {}", firstValidIndex);
        if( next != null) next.init(segments.isEmpty() ? lastInitialized : segments.last());
    }

    private boolean indexValid(long segment) {
        if( indexManager.validIndex(segment)) {
            return true;
        }

        recreateIndex(segment);
        return false;
    }

    private void recreateIndex(long segment) {
        ByteBufferEventSource buffer = get(segment);
        EventByteBufferIterator iterator = new EventByteBufferIterator(buffer, segment, segment);
        Map<String, SortedSet<PositionInfo>> aggregatePositions = new HashMap<>();
        while( iterator.hasNext()) {
            EventInformation event = iterator.next();
            if( isDomainEvent(event.getEvent())) {
                aggregatePositions.computeIfAbsent(event.getEvent().getAggregateIdentifier(),
                                                   k -> new ConcurrentSkipListSet<>())
                                  .add(new PositionInfo(event.getPosition(),
                                                        event.getEvent().getAggregateSequenceNumber()));
            }
        }
        indexManager.createIndex(segment, aggregatePositions, true);

    }

    @Override
    protected Optional<EventSource> getEventSource(long segment) {
        return Optional.ofNullable(get(segment));
    }

    @Override
    protected SortedSet<Long> getSegments() {
        return segments;
    }

    @Override
    protected SortedSet<PositionInfo> getPositions(long segment, String aggregateId) {
        return indexManager.getPositions(segment, aggregateId);
    }

    @Override
    public void handover(Long segment, Runnable callback) {
        segments.add(segment);
        if( next != null && segments.size() > storageProperties.getNumberOfSegments()) {
            segments.stream().skip(storageProperties.getNumberOfSegments()).forEach(s -> next.handover(s, () -> {
                segments.remove(s);
                indexManager.remove(s);
                WeakReference<ByteBufferEventSource> fileRef = lruMap.remove(s);
                if( fileRef != null) {
                    ByteBufferEventSource file = fileRef.get();
                    if( file != null) {
                        file.clean(storageProperties.getSecondaryCleanupDelay());
                    }
                }
                scheduledExecutorService.schedule(()-> deleteFiles(s), 20, TimeUnit.SECONDS);
            }));
        }
        callback.run();
    }

    private void deleteFiles(Long s) {
        logger.debug("Deleting {} files for segment {}", getType().getEventType(), s);
        File bloomFilter = storageProperties.bloomFilter(context, s);
        File index = storageProperties.index(context, s);
        File datafile = storageProperties.dataFile(context, s);
        boolean success = FileUtils.delete(bloomFilter) && FileUtils.delete(index) && FileUtils.delete(datafile);

        if( ! success) {
            logger.debug("Deleting {} files for segment {} not complete, rescheduling", getType().getEventType(), s);
            scheduledExecutorService.schedule(()-> deleteFiles(s), 1, TimeUnit.MINUTES);
        }
    }

    @Override
    public void cleanup() {
        lruMap.forEach((s, source) -> {
            ByteBufferEventSource eventSource = source.get();
            if( eventSource != null) {
                eventSource.clean(5);
            }
        });
        indexManager.cleanup();
    }

    @Override
    public PreparedTransaction prepareTransaction(List<Event> eventList) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void rollback( long token) {
        for( long segment: getSegments()) {
            if( segment > token) {
                removeSegment(segment);
            }
        }

        if( segments.isEmpty() && next != null) {
            next.rollback(token);
        }
    }

    private void removeSegment(long segment) {
        if( segments.remove(segment)) {
            WeakReference<ByteBufferEventSource> segmentRef = lruMap.remove(segment);
            if (segmentRef != null) {
                ByteBufferEventSource eventSource = segmentRef.get();
                if (eventSource != null) {
                    eventSource.clean(0);
                }
            }

            indexManager.remove(segment);
            FileUtils.delete(storageProperties.dataFile(context, segment));
            FileUtils.delete(storageProperties.index(context, segment));
            FileUtils.delete(storageProperties.bloomFilter(context, segment));
        }
    }


    private ByteBufferEventSource get(long segment)  {
        if( ! segments.contains(segment)) return null;
        WeakReference<ByteBufferEventSource> bufferRef = lruMap.get(segment);
        if( bufferRef != null ) {
            ByteBufferEventSource b =  bufferRef.get();
            if( b != null) {
                return b.duplicate();
            }
        }

        File file = storageProperties.dataFile(context, segment);
        long size = file.length();

        try(FileChannel fileChannel = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            ByteBufferEventSource eventSource = new ByteBufferEventSource(buffer, eventTransformerFactory, storageProperties);
            lruMap.put(segment, new WeakReference<>(eventSource));
            return eventSource;
        } catch (IOException ioException) {
            throw new MessagingPlatformException(ErrorCode.DATAFILE_READ_ERROR, "Error while opening segment: " + segment, ioException);
        }
    }

}
