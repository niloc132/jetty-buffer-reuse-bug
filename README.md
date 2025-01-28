# Jetty bug : AsyncContext.complete() sends RST_STREAM(cancel), possible

**Jetty version(s)**
Reproduced with Jetty 11, Cannot repro with 12

**Jetty Environment**
N/A

**Java version/vendor** `(use: java -version)`
$ java -version
openjdk version "21.0.5" 2024-10-15
OpenJDK Runtime Environment (build 21.0.5+11)
OpenJDK 64-Bit Server VM (build 21.0.5+11, mixed mode, sharing)

**OS type/version**
$ uname -a
Linux runes 6.12.7-arch1-1 #1 SMP PREEMPT_DYNAMIC Fri, 27 Dec 2024 14:24:37 +0000 x86_64 GNU/Linux

**Description**
Ending an async servlet response on the server appears to only be possible via `AsyncContext.complete()`. In situations
where the client hasn't half-closed from its side, the server must send a RST_STREAM frame to the client to indicate that
the stream is over, and no more messages may be sent or received.

However, the servlet specification doesn't describe which "error" code should be sent in this case. My own expectation 
might be that without an actual error condition, the client would receive the "noerror" value. This is the behavior that
Tomcat and Wildfly exhibit.

Jetty 11 and 12, on the other hand, send the "cancel" error code in this situation. 

While gRPC clients ostensibly check http2 trailers for the `grpc-status` value, and not the RST_STREAM error code, the 
grpc specification does allow the RST_STREAM error code to be mapped to grpc-status values. This means that a client 
that receives 'cancel' might interpret the stream to have failed, rather than being intentionally closed by the server 
successfully.

As a workaround, it is possible to call `ServletOutputStream.close()` in place of calling `AsyncContext.complete()`.
This results in the server instead sending trailers (unless there were no trailers, then an empty DATA is sent) with 
endStream set.

Unfortunately, other servlet containers seem to send "cancel" after ServletOutputStream.close() is called, or just don't
close, so there is no good cross-container solution. If complete() is called synchronously after close(), Jetty will send "cancel" anyway - I 
haven't figured out an appropriate delay to avoid this.

Additionally, if `ServletOutputStream.close()` is called, Jetty will not consider that to be the end of the stream and
if a timeout was set via `AsyncContext.setTimeout()`, the timeout will still be triggered, resulting in errors on the
server. Using this workaround, timeouts should simply be avoided.

**How to reproduce?**



