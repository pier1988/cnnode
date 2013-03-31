import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class dvnode {
	static int SelfPort;
	static DatagramSocket Socket;
	public static boolean IsFirst = true;
	
	public static void main(String[] args) throws IOException, InterruptedException {
		InetAddress IPAddress = InetAddress.getByName("localhost");
		SelfPort = Integer.parseInt(args[0]);
		boolean LastNode = false;
		ArrayList<RouteTable> RouteTable1 = new ArrayList<RouteTable>();
		ArrayList<TimeStamp> TimeStamp1 = new ArrayList<TimeStamp>();
		ArrayList<Thread> ThreadList = new ArrayList<Thread>();
		ArrayList<Neig> NeigList1 = new ArrayList<Neig>();
		int ThreadNum = 0;
						
		
		if (SelfPort>=1024 && SelfPort<=65534) {
			Socket = new DatagramSocket(SelfPort);
			if (args[args.length-1].equals("last")) {
				LastNode = true;
			}	//end if
			for (int i=1; 2*i<args.length; i++) {
				int tempport = Integer.parseInt(args[2*i-1]);
				float tempdist = Float.parseFloat(args[2*i]);
				if (tempport>=1024 && tempport<=65534) {
					NeigList1.add(new Neig(tempport, tempdist));
					RouteTable1.add(new RouteTable(tempport, tempdist,tempport));
					TimeStamp1.add(new TimeStamp(tempport));
				}	//end if
				else {System.out.println("Port number is out of range(1024 ~ 65534)"); return;}
			}	//end for
			
			PrintRouteTable(RouteTable1);	//initial routing table
			
			if (LastNode) {
				BroadCastTable(TimeStamp1, RouteTable1);
			}	//end if
			
			while (true) {	//listen for DV packet
				byte[] receiveData = new byte[64];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					Socket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				String ReceivedInfo = DataToString(receivePacket);
				String[] rcvinfo = ReceivedInfo.split(" ");
				if (rcvinfo[0].equals("r")) {	
					System.out.println("[" + System.currentTimeMillis() + "] Message received at Node " + Integer.toString(SelfPort) + " from Node " + rcvinfo[1]);
					ThreadList.add(ThreadNum, new Thread(new DVUpdate(ReceivedInfo, TimeStamp1, RouteTable1, NeigList1, SelfPort, Socket)));
					ThreadList.get(ThreadNum).start();
					ThreadList.get(ThreadNum).join();
					ThreadNum++;
				}
			}	//end while
		}	else {System.out.println("Port number is out of range(1024 ~ 65534)");}

	}	//end main
	
	public static void BroadCastTable(ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table) throws IOException {
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] sendData = new byte[64];
		String SendInfo = "r " + Integer.toString(SelfPort) + " " + Float.toString(System.currentTimeMillis());
		for (int i=0; i<table.size(); i++) {
			SendInfo = SendInfo + " " + Integer.toString(table.get(i).GetDestPort()) + " " + Float.toString(table.get(i).GetDist());
		}	//end for
		sendData = SendInfo.getBytes();
		for (int i=0; i<timestamp.size(); i++) {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, timestamp.get(i).GetNeigPort());
			Socket.send(sendPacket);
			System.out.println("[" + System.currentTimeMillis() + "] Message sent from Node " + Integer.toString(SelfPort) + " to Node " + Integer.toString(timestamp.get(i).GetNeigPort()));
		}
	}	//end method
	
	public static void PrintRouteTable(ArrayList<RouteTable> table) {
		System.out.println("["+System.currentTimeMillis()+"] Node " + SelfPort + " Routing Table");
		for (int i=0; i<table.size(); i++) {
			System.out.print("- ("); 
			System.out.printf("%.1f",table.get(i).GetDist());
			System.out.print(") -> Node " + Integer.toString(table.get(i).GetDestPort()));
			if (table.get(i).GetDestPort() == table.get(i).GetNextHop()) {
				System.out.print("\n");
			}	//end if
			else {
				System.out.println(" ; Next hop -> Node " + table.get(i).GetNextHop());
			}	//end else
		}	//end for 
	}	// end method
	
	static String DataToString(DatagramPacket packet) {	//remove useless space
		byte[] buffer = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, buffer, 0, packet.getLength());
		String ReceivedInfo = new String(buffer);	
		return ReceivedInfo;
	}	//end method

}	//end class dvnode

//thread to deal DV packets
class DVUpdate implements Runnable {
	String RcvInfo;
	ArrayList<TimeStamp> TimeStamp;
	ArrayList<RouteTable> Table;
	ArrayList<Neig> NeigList;
	static int SelfPort;
	static DatagramSocket Socket;
	//volatile boolean IsFirst = true;
	
	public DVUpdate(String str, ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table, ArrayList<Neig> neiglist, int port, DatagramSocket socket) {
		RcvInfo = str;
		TimeStamp = timestamp;
		Table = table;
		SelfPort = port;
		Socket = socket;
		NeigList = neiglist;
	}	//end constructor
	
	public synchronized void run() {
		boolean IsUpdate = false;
		String[] rcvinfo = RcvInfo.split(" ");
		float w = 100;
		//whether message is up-to-date
		for (int i=0; i<TimeStamp.size(); i++) {
			if ((Integer.parseInt(rcvinfo[1]) == TimeStamp.get(i).GetNeigPort()) && (Float.parseFloat(rcvinfo[2])>TimeStamp.get(i).GetTime())) {
				TimeStamp.get(i).SetTime(Float.parseFloat(rcvinfo[2]));
			}	//end if
			else if ((Integer.parseInt(rcvinfo[1]) == TimeStamp.get(i).GetNeigPort()) && (Float.parseFloat(rcvinfo[2])<TimeStamp.get(i).GetTime())) {
				return;			//old packet
			}	//end else if
		}	//end for
		
		//get weight of link
		for (int i=0; i<NeigList.size(); i++) {
			if (NeigList.get(i).GetNeigPort() == Integer.parseInt(rcvinfo[1])) {
				w = NeigList.get(i).GetDist();
			}	//end if
		}	//end if
		
		//Bellman-Ford alg
		for (int i=0; (2*i+3)<rcvinfo.length; i++) {
			boolean RouteExist = false;
			for (int j=0; j<Table.size(); j++) {
				if (Table.get(j).GetDestPort() == Integer.parseInt(rcvinfo[2*i+3]) && Table.get(j).GetDist()>(w + Float.parseFloat(rcvinfo[2*i+4]))) {
					Table.get(j).SetDist((w + Float.parseFloat(rcvinfo[2*i+4])));
					Table.get(j).SetNextHop(Integer.parseInt(rcvinfo[1]));
					IsUpdate = true;
					RouteExist = true;
				}	//end if
				else if (Table.get(j).GetDestPort() == Integer.parseInt(rcvinfo[2*i+3]) && Table.get(j).GetDist()<=(w + Float.parseFloat(rcvinfo[2*i+4]))) {
					RouteExist = true;
				}	//end else if
			}
			if (!RouteExist && Integer.parseInt(rcvinfo[2*i+3])!=SelfPort) {
				Table.add(new RouteTable(Integer.parseInt(rcvinfo[2*i+3]), (w + Float.parseFloat(rcvinfo[2*i+4])), Integer.parseInt(rcvinfo[1])));
				IsUpdate = true;
			}	//end if
		}	//end for

		
		if (IsUpdate || dvnode.IsFirst) {
			if (IsUpdate) {
				PrintRouteTable(Table);
			}	//end if
			try {
				BroadCastTable(TimeStamp, Table);
				dvnode.IsFirst = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	//end if
	}	//end run
	
	public static synchronized void BroadCastTable(ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table) throws IOException {
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] sendData = new byte[64];
		String SendInfo = "r " + Integer.toString(SelfPort) + " " + Float.toString(System.currentTimeMillis());
		for (int i=0; i<table.size(); i++) {
			SendInfo = SendInfo + " " + Integer.toString(table.get(i).GetDestPort()) + " " + Float.toString(table.get(i).GetDist());
		}	//end for
		sendData = SendInfo.getBytes();
		for (int i=0; i<timestamp.size(); i++) {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, timestamp.get(i).GetNeigPort());
			Socket.send(sendPacket);
			System.out.println("[" + System.currentTimeMillis() + "] Message sent from Node " + Integer.toString(SelfPort) + " to Node " + Integer.toString(timestamp.get(i).GetNeigPort()));
		}
	}	//end method
	
	public static synchronized void PrintRouteTable(ArrayList<RouteTable> table) {
		System.out.println("["+System.currentTimeMillis()+"] Node " + SelfPort + " Routing Table");
		for (int i=0; i<table.size(); i++) {
			System.out.print("- ("); 
			System.out.printf("%.1f",table.get(i).GetDist());
			System.out.print(") -> Node " + Integer.toString(table.get(i).GetDestPort()));
			if (table.get(i).GetDestPort() == table.get(i).GetNextHop()) {
				System.out.print("\n");
			}	//end if
			else {
				System.out.println(" ; Next hop -> Node " + table.get(i).GetNextHop());
			}	//end else
		}	//end for 
	}	// end method
}	//end class DVUpdate

class RouteTable {
	private int DestPort;
	private float Dist;
	private int NextHop;
	
	public RouteTable(int dest, float dist, int next) {
		DestPort = dest;
		Dist = dist;
		NextHop = next;
	}	//end construtor
	
	public int GetDestPort() {
		return DestPort;
	}	//end method
	
	public float GetDist() {
		return Dist;
	}	//end method
	
	public int GetNextHop() {
		return NextHop;
	}	//end method	
	
	public void SetDist(float dist) {
		Dist = dist;
	}	//end method
	
	public void SetNextHop(int hop) {
		NextHop = hop;
	}	//end method
	
}	//end class RouteTable

class TimeStamp {
	private int NeigPort;
	private float Time;
	
	public TimeStamp(int port) {
		NeigPort = port;
		Time = 0;
	}
	
	public float GetTime() {
		return Time;
	}	//end method
	
	public int GetNeigPort() {
		return NeigPort;
	}	//end method	
	
	public void SetTime(float time) {
		Time = time;
	}	//end method
}

class Neig {
	private int NeigPort;
	private float Dist;
	
	public Neig(int port, float dist) {
		NeigPort = port;
		Dist = dist;
	}	//end constructor
	
	public int GetNeigPort() {
		return NeigPort;
	}	//end method
	
	public float GetDist() {
		return Dist;
	}	//end method
}	//end class Neig
	
