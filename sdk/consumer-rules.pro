# Consumer rules for an app that minifies with R8.
#
# grpc-binder's name resolver (since gRPC 1.76): on Android, NameResolverRegistry skips
# ServiceLoader and walks a hard-coded list of class names, calling
# Class.forName(...).getConstructor().newInstance() on each. R8 keeps the class, because it follows
# the constant forName, but it drops the no-arg constructor, because nothing in the bytecode calls
# it. Every BinderChannelBuilder.forAddress then throws ServiceConfigurationError and no channel
# opens. That hits a host, the side that opens channels; a companion's server side does not reach
# it. Do not remove it as unused: the constructor is only ever called by reflection.
-keepclassmembers class io.grpc.binder.internal.IntentNameResolverProvider {
    public <init>();
}
