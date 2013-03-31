cnnode
======

Combination of GBN protocol and  distance-vector protocol

1. Go-Back-N Protocol
Program name: gbnnode.class
Function
There are two nodes. The sender sends packets to the receiver through the UDP protocol. They use the Go-Back-N (GBN) protocol on top of UDP on both nodes to guarantee that all packets can be successfully delivered to the higher layers in the correct order. To emulate an unreliable channel, the receiver or the sender needs to drop an incoming data packet or an ACK, respectively, with a certain probability.


2. Distance-Vector Routing Algorithm
Program name: dvnode.class
Function
A simple version of a routing protocol in a static network. A program which builds its routing table based on the distances (i.e., edge weights) to other nodes in the network. The Bellman-Ford algorithm should be used to build and update the routing tables. The UDP protocol should be used to exchange the routing table information among the nodes in the network.

3. Combination
Program name: cnnode.class
Function
The combination of above two protocols. The GBN protocol can create reliable links, and the DV algorithms should determine the shortest paths over these GBN links. The distance of each link will be the packet loss rate on that link calculated by the GBN protocol.
