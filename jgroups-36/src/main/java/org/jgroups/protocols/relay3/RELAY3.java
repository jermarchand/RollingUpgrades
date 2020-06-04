package org.jgroups.protocols.relay3;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.relay_server.*;
import org.jgroups.stack.Protocol;
import org.jgroups.util.UUID;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Relays application messages to the RelayServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
@MBean(description="Protocol that redirects all messages to/from a RelayServer")
public class RELAY3 extends Protocol {

    @Property(description="Whether or not to perform relaying via the relay server",writable=false)
    protected volatile boolean                          active;

    @Property(description="The IP address (or symbolic name) of the relay server")
    protected String                                    server_address="localhost";

    @Property(description="The port on which the relay server is listening")
    protected int                                       server_port=50151;

    @ManagedAttribute(description="The local address")
    protected Address                                   local_addr;

    @ManagedAttribute(description="Shows the local view")
    protected org.jgroups.View                          local_view;

    @ManagedAttribute(description="The global view (provided by the RelayServer)")
    protected org.jgroups.View                          global_view;

    @ManagedAttribute(description="The cluster this member is a part of")
    protected String                                    cluster;
    protected ManagedChannel                            channel;
    protected RelayServiceGrpc.RelayServiceStub         asyncStub;
    protected StreamObserver<Request>                   send_stream; // for sending of messages and join requests


    @ManagedOperation(description="Enable forwarding and receiving of messages to/from the RelayServer")
    public synchronized void activate() {
        if(!active) {
            connect(cluster);
            active=true;
        }
    }

    @ManagedOperation(description="Disable forwarding and receiving of messages to/from the RelayServer")
    public synchronized void deactivate() {
        if(active) {
           disconnect();
           active=false;
        }
    }


    public void start() throws Exception {
        super.start();
        channel=ManagedChannelBuilder.forAddress(server_address, server_port).usePlaintext().build();
        asyncStub=RelayServiceGrpc.newStub(channel);
    }

    public void stop() {
        super.stop();
        channel.shutdown();
    }


    public Object down(Event evt) {
        switch(evt.type()) {
            case Event.MSG:
                if(!active)
                    return down_prot.down(evt);
                // else send to RelayServer
                if(send_stream != null) {
                    Message msg=(Message)evt.getArg();
                    Request req=Request.newBuilder().setMessage(jgroupsMessageToProtobufMessage(cluster, msg)).build();
                    send_stream.onNext(req);
                }
                return null;
            case Event.SET_LOCAL_ADDRESS:
                local_addr=evt.arg();
                break;
            case Event.CONNECT:
            case Event.CONNECT_USE_FLUSH:
            case Event.CONNECT_WITH_STATE_TRANSFER:
            case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH:
                cluster=evt.arg();
                Object ret=down_prot.down(evt);
                if(active)
                    connect(cluster);
                return ret;
            case Event.DISCONNECT:
                ret=down_prot.down(evt);
                disconnect();
                return ret;
        }
        return down_prot.down(evt);
    }

    public Object up(Event evt) {
        switch(evt.type()) {
            case Event.VIEW_CHANGE:
                local_view=evt.arg();
                if(active)
                    return null;
                break;
        }
        return up_prot.up(evt);
    }



    protected synchronized void connect(String cluster) {
        send_stream=asyncStub.connect(new StreamObserver<Response>() {
            public void onNext(Response rsp) {
                if(rsp.hasMessage()) {
                    handleMessage(rsp.getMessage());
                    return;
                }
                if(rsp.hasView()) {
                    handleView(rsp.getView());
                    return;
                }
                throw new IllegalStateException(String.format("response is illegal: %s", rsp));
            }

            public void onError(Throwable t) {
                log.error("exception from server: %s", t);
            }

            public void onCompleted() {
                log.debug("server is done");
            }
        });
        org.jgroups.relay_server.Address pbuf_addr=jgroupsAddressToProtobufAddress(local_addr);
        JoinRequest join_req=JoinRequest.newBuilder().setAddress(pbuf_addr).setClusterName(cluster).build();
        Request req=Request.newBuilder().setJoinReq(join_req).build();
        send_stream.onNext(req);
    }



    protected synchronized void disconnect() {
        if(send_stream != null) {
            if(local_addr != null && cluster != null) {
                org.jgroups.relay_server.Address local=jgroupsAddressToProtobufAddress(local_addr);
                LeaveRequest leave_req=LeaveRequest.newBuilder().setClusterName(cluster).setLeaver(local).build();
                Request request=Request.newBuilder().setLeaveReq(leave_req).build();
                send_stream.onNext(request);
            }
            send_stream.onCompleted();
        }
        global_view=null;
    }

    protected void handleView(View view) {
        // System.out.printf("received view %s\n", print(view));
        org.jgroups.View jg_view=protobufViewToJGroupsView(view);
        global_view=jg_view;
        up_prot.up(new Event(Event.VIEW_CHANGE, jg_view));

    }

    protected void handleMessage(org.jgroups.relay_server.Message m) {
        // System.out.printf("received message %s\n", print(m));
        Message msg=protobufMessageToJGroupsMessage(m);
        up_prot.up(new Event(Event.MSG, msg));
    }

    protected static org.jgroups.relay_server.Address jgroupsAddressToProtobufAddress(Address jgroups_addr) {
        if(jgroups_addr == null)
            return org.jgroups.relay_server.Address.newBuilder().build();
        if(!(jgroups_addr instanceof org.jgroups.util.UUID))
            throw new IllegalArgumentException(String.format("JGroups address has to be of type UUID but is %s",
                                                             jgroups_addr.getClass().getSimpleName()));
        UUID uuid=(UUID)jgroups_addr;
        String name=UUID.get(jgroups_addr);

        org.jgroups.relay_server.UUID pbuf_uuid=org.jgroups.relay_server.UUID.newBuilder()
          .setLeastSig(uuid.getLeastSignificantBits()).setMostSig(uuid.getMostSignificantBits()).build();
        return org.jgroups.relay_server.Address.newBuilder().setUuid(pbuf_uuid).setName(name).build();
    }

    protected static Address protobufAddressToJGroupsAddress(org.jgroups.relay_server.Address pbuf_addr) {
        if(pbuf_addr == null)
            return null;
        org.jgroups.relay_server.UUID pbuf_uuid=pbuf_addr.hasUuid()? pbuf_addr.getUuid() : null;
        return pbuf_uuid == null? null : new UUID(pbuf_uuid.getMostSig(), pbuf_uuid.getLeastSig());
    }

    protected static org.jgroups.relay_server.Message jgroupsMessageToProtobufMessage(String cluster, Message jgroups_msg) {
        if(jgroups_msg == null)
            return null;
        Address destination=jgroups_msg.getDest(), sender=jgroups_msg.getSrc();
        byte[] payload=jgroups_msg.getBuffer();

        org.jgroups.relay_server.Message.Builder msg_builder=org.jgroups.relay_server.Message.newBuilder()
          .setClusterName(cluster);
        if(destination != null)
            msg_builder.setDestination(jgroupsAddressToProtobufAddress(destination));
        if(sender != null)
            msg_builder.setSender(jgroupsAddressToProtobufAddress(sender));
        if(payload != null)
            msg_builder.setPayload(ByteString.copyFrom(payload));
        return msg_builder.build();
    }

    protected static Message protobufMessageToJGroupsMessage(org.jgroups.relay_server.Message msg) {
        Message jgroups_mgs=new Message();
        if(msg.hasDestination())
            jgroups_mgs.setDest(protobufAddressToJGroupsAddress(msg.getDestination()));
        if(msg.hasSender())
            jgroups_mgs.setSrc(protobufAddressToJGroupsAddress(msg.getSender()));
        ByteString payload=msg.getPayload();
        if(!payload.isEmpty())
            jgroups_mgs.setBuffer(payload.toByteArray());
        return jgroups_mgs;
    }

    protected static org.jgroups.View protobufViewToJGroupsView(View v) {
        ViewId pbuf_vid=v.getViewId();
        List<org.jgroups.relay_server.Address> pbuf_mbrs=v.getMemberList();
        org.jgroups.ViewId jg_vid=new org.jgroups.ViewId(protobufAddressToJGroupsAddress(pbuf_vid.getCreator()),
                                                         pbuf_vid.getId());
        List<Address> members=new ArrayList<>();
        pbuf_mbrs.stream().map(RELAY3::protobufAddressToJGroupsAddress).forEach(members::add);
        return new org.jgroups.View(jg_vid, members);
    }

    protected static String print(org.jgroups.relay_server.Message msg) {
        return String.format("cluster: %s sender: %s dest: %s %d bytes\n", msg.getClusterName(),
                             msg.hasDestination()? msg.getDestination().getName() : "null",
                             msg.hasSender()? msg.getSender().getName() : "null",
                             msg.getPayload().isEmpty()? 0 : msg.getPayload().size());
    }

    public static String print(View v) {
        if(v.hasViewId()) {
            ViewId view_id=v.getViewId();
            return String.format("%s|%d [%s]",
                          view_id.getCreator().getName(), view_id.getId(),
                          v.getMemberList().stream().map(org.jgroups.relay_server.Address::getName)
                                   .collect(Collectors.joining(", ")));
        }
        return String.format("[%s]",
                             v.getMemberList().stream().map(org.jgroups.relay_server.Address::getName)
                               .collect(Collectors.joining(", ")));
    }


}
