# Consumer rules: R8 reads META-INF/proguard/*.pro from every jar on a minifying app's classpath.
#
# protobuf-javalite looks each field of a generated message up by the name in its generated
# newMessageInfo string (status_, bitField0_, and so on). R8 removes or renames any field the app
# never reads, so the first parse of that message then throws "Field status_ for ... not found",
# whatever the wire holds. That affects both a host and a companion, for any message whose fields
# the app does not read. Scoped to this module's generated package, which includes the vendored
# google.rpc messages. Do not remove it as unused: nothing in the bytecode names these fields.
-keepclassmembers class com.binge.companion.contracts.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
