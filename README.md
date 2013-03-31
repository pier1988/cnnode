cnnode
======

Combination of GBN protocol and  distance-vector protocol

1. Go-Back-N Protocol
Program name: gbnnode.class
Function
There are two nodes. The sender sends packets to the receiver through the UDP protocol. They use the Go-Back-N (GBN) protocol on top of UDP on both nodes to guarantee that all packets can be successfully delivered to the higher layers in the correct order. To emulate an unreliable channel, the receiver or the sender needs to drop an incoming data packet or an ACK, respectively, with a certain probability.
Features
●Data packet structure
SeqNum + “space” + Data
SeqNum: sequence number of the packet. Type:integer
Data: one character of the keyboard input. Type:char
The ACK packet is similar to the data packet except that it only has the header and does not have any data in it.
●Buffer
Each node has a sending buffer. All data packets (not ACK) will be put into the buffer before sending, and removed from the buffer once the corresponding ACK is received. In this program, the buffer is implemented as an array and the size of it is set to 100 to guarantee no sequence number conflict.  If the buffer is full, the sending function simply waits until more space is available.
●Window
The window moves along the sending buffer. The window can move back to the beginning of the array after reaching the end of the buffer. Packets in the window will be sent out immediately. The size for the window will be passed in as an argument when starting the program.
●Timer
There is only one timer for GBN protocol. It starts after the ﬁrst packet in the window is sent out, and stops when the ACK for the first packet in the window is received. After the window moves, if the first packet of the new window has already been sent out, the timer will simply restart; otherwise it should stop and wait for the first packet. The timeout for the timer should be 500ms and all the packets in the window should be resent after timeout.
●Command
$ java gbnnode <self-port> <peer-port> <window-size> [ -d <value-of-n> j -p <value-of-p>]
The user should only specify either -d or -p. The square bracket and the vertical line mean to choose between the two options.
-d means the GBN node will drop packets (data or ACK) in a deterministic way (for every n packets).
-p means the GBN node will drop packets with a probability of p.
node> send <message>
message is a string which the sender will send to the <peer-port>
●Status Message
Sender
[<timestamp>] packet<packet-num> <packet-content> sent
[<timestamp>] ACK<packet-num> received, window moves to <packet-num>
[<timestamp>] ACK<packet-num> discarded
[<timestamp>] packet<packet-num> timeout
Receiver
[<timestamp>] packet<packet-num> <packet-content> received
[<timestamp>] packet<packet-num> <packet-content> discarded
[<timestamp>] ACK<packet-num> sent, expecting packet<packet-num>

2. Distance-Vector Routing Algorithm
Program name: dvnode.class
Function
A simple version of a routing protocol in a static network. A program which builds its routing table based on the distances (i.e., edge weights) to other nodes in the network. The Bellman-Ford algorithm should be used to build and update the routing tables. The UDP protocol should be used to exchange the routing table information among the nodes in the network.
Feature
●Network Model
We assume that that all the nodes run on the same machine and they all have the same IP address. Each node can be identified uniquely by a (UDP listening) port number, which is specified by the user. The port numbers must be > 1024. The maximum number of nodes to support is 16. The links among the nodes in the network and the distances (non-negative integer) between two directly connected nodes are specified by the user upon the activation of the program and stay static throughout the session. The link distance is the same for either direction.
●Bellman-Ford algorithm
The node uses Bellman-Ford algorithm to calculate the routing table in itself.  The key idea of Bellman-Ford algorithm is “relax the edge”, which is shown below:
    for each edge uv in edges: // uv is the edge from u to v
           u := uv.source
           v := uv.destination
           if u.distance + uv.weight < v.distance:
               v.distance := u.distance + uv.weight
               v.predecessor := u
Every node will find the minimum distance to any reachable node by this algorithm.
●Command
$ dvnode <local-port> <neighbor1-port> <loss-rate-1> <neighbor2-port> <loss-rate-2> ... [last]
<local-port> The UDP listening port number (1024-65534) of the node.
<neighbor#-port> The UDP listening port number (1024-65534) of one of the neighboring nodes.
<loss-rate-#> This will be used as the link distance to the <neighbor#-port>.It is between 0-1 and represents the probability of a packet being dropped on that link. For this section of the assignment you do not have to implement dropping of packets.Keep listing the pair of <neighbor-port> and <loss-rate> for all your neighboring nodes.
last Optional. Indication of the last node information of the network. Upon the input of the
command with this argument, the routing message exchanges among the nodes should kick in.
●Status Messages
1. Routing table (every time after a message is received, and for the initial routing table):
[<timestamp>] Node <port-xxxx> Routing Table
- (<distance>) -> Node <port-yyyy>
- (<distance>) -> Node <port-zzzz> ; Next hop -> Node <port-yyyy>
- (<distance>) -> Node <port-vvvv>
- (<distance>) -> Node <port-wwww> ; Next hop -> Node <port-vvvv>
2. Routing message sent:
[<timestamp>] Message sent from Node <port-xxxx> to Node <port-vvvv>
3. Routing message received:
[<timestamp>] Message received at Node <port-vvvv> from Node <port-xxxx>

3. Combination
Program name: cnnode.class
Function
The combination of above two protocols. The GBN protocol can create reliable links, and the DV algorithms should determine the shortest paths over these GBN links. The distance of each link will be the packet loss rate on that link calculated by the GBN protocol.
Feature
●Loss rate calculation
Window size is always 5.
The data packet will only be dropped in a probabilistic way.
ACK will never be dropped.
Timeout is still 500ms.
The loss rate of each link is calculated by the following equation:
Link cost = (Total number of dropped packets/Total number of sent packets) and initial 0;
●Probe packets
To obtain the loss rate quickly, probe packets (data packets with any data) should be sent continuously (in one direction) on all links. Because each link has two nodes, the user has to specify which node is the probe sender, and which node is the probe receiver. Also, the sender should only start to send probe packets when all nodes in the network have become ready. As described in Section 3, there will be a last node with the word “last” as the argument. Once that node is started, the DV update messages should be sent to all that last node’s neighbors, triggering the neighbors to send out DV messages as well. Therefore, the first time a node receives a DV update message, it should know that the network has become ready, and it can start sending probe packets.
●Routing information exchange
The distances in the routing table should be rounded to the second decimal place.
The updates of routing table should only be sent out 1) for every 5 seconds (if there is any change in the rounded distances based on the newly calculated loss rate), or 2) when a DV update is received from a neighbor that changes the local routing table.
The updates should be sent to all the neighbors, including all probe senders and receivers.
The probe receivers will only get the calculated distance of the corresponding links through the updates sent by the probe senders.
●Command
cnnode <local-port> receive <neighbor1-port> <loss-rate-1> <neighbor2-port> <loss-rate-2> ... <neighborM-port> <loss-rate-M> send <neighbor(M+1)-port> <neighbor(M+2)-port> ... <neighborN-port> [last]
<local-port> The UDP listening port number (1024-65534) of the node.
receive The current node will be the probe receiver for the following neighbors.
<neighbor#-port> The UDP listening port number (1024-65534) of one of the neighboring nodes. If you are using a different sending port number, you have to add the listening port number to your own packet header.
<loss-rate-#> The probability to drop the probe packets. Keep listing the pair of <sender-port> and <loss-rate> for all your neighboring nodes.
send The current node will be the probe sender for the following neighbors. Keep listing the pair of <neighbor-port> and <loss-rate> for all your neighboring nodes.
last Optional. Indication of the last node information of the network. Upon the input of the command with this argument, the routing message exchanges among the nodes should kick in.
