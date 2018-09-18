package io.axoniq.axonserver.grpc;


import io.axoniq.axonserver.internal.grpc.Action;
import io.axoniq.axonserver.internal.grpc.Application;
import io.axoniq.axonserver.internal.grpc.ApplicationRole;
import io.axoniq.axonserver.internal.grpc.User;
import io.axoniq.platform.user.UserRole;

import java.util.Date;
import java.util.stream.Collectors;

/**
 * Author: marc
 */
public class ProtoConverter {

    public static io.axoniq.platform.application.jpa.Application createJpaApplication(Application application) {
        io.axoniq.platform.application.jpa.ApplicationRole[] roles = new io.axoniq.platform.application.jpa.ApplicationRole[application.getRolesCount()];
        for( int i = 0; i < application.getRolesCount(); i++) {
            ApplicationRole role = application.getRoles(i);
            roles[i] = new io.axoniq.platform.application.jpa.ApplicationRole(role.getName(),
                    role.getContext(),
                    role.getEndDate() > 0 ? new Date(role.getEndDate()): null);
        }

        return new io.axoniq.platform.application.jpa.Application(application.getName(),application.getDescription(), application.getTokenPrefix(),
                application.getHashedToken(), roles);
    }

    public static Application createApplication(io.axoniq.platform.application.jpa.Application app, Action action) {
        Application.Builder builder = Application.newBuilder().setName(app.getName()).setAction(action);
        if( app.getDescription()!= null)
                builder.setDescription(app.getDescription());
        if( app.getHashedToken() != null)
                builder.setHashedToken(app.getHashedToken());
        if( app.getTokenPrefix() != null)
            builder.setTokenPrefix(app.getTokenPrefix());
        app.getRoles().forEach(role ->
                builder.addRoles(ApplicationRole.newBuilder()
                        .setName(role.getRole())
                        .setContext(role.getContext())
                        .setEndDate(role.getEndDate() != null? role.getEndDate().getTime(): 0)
                        .build())
        );
        return builder.build();
    }

    public static User createUser(io.axoniq.platform.user.User user, Action action) {
        return User.newBuilder().setAction(action)
            .setName(user.getUserName())
            .setPassword(user.getPassword() == null? "" : user.getPassword())
            .addAllRoles(user.getRoles()
                             .stream()
                             .map(UserRole::getRole)
                             .collect(Collectors.toSet()))
            .build();
    }

    public static User createUser(String name, Action action) {
        return User.newBuilder().setAction(action)
                   .setName(name)
                   .build();
    }

    public static io.axoniq.platform.user.User createJpaUser(User user) {
        return new io.axoniq.platform.user.User(user.getName(), user.getPassword(), user.getRolesList().toArray(new String[0]));
    }
}
