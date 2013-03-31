import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class cnnode {

	static int SelfPort;
	static DatagramSocket Socket;
	static DatagramSocket Socket1;
	public static boolean IsFirst = true;
	volatile static ArrayList<Neig> NeigList1 = new ArrayList<Neig>();
	static ArrayList<Neig> FormerNeigList1 = new ArrayList<Neig>();
	volatile static ArrayList<RouteTable> RouteTable1 = new ArrayList<RouteTable>();
	static ArrayList<ReceiveNeig> ReceiveNeigList1 = new ArrayList<ReceiveNeig>();
	static ArrayList<SendingBuffer> SendingBufferList1 = new ArrayList<SendingBuffer>();
	static ArrayList<TimeStamp> TimeStamp1 = new ArrayList<TimeStamp>();
	
	
	public static void main(String[] args) throws IOException, InterruptedException {
		InetAddress IPAddress = InetAddress.getByName("localhost");
		SelfPort = Integer.parseInt(args[0]);
		boolean LastNode = false;
		ArrayList<SendNeig> SendNeigList1 = new ArrayList<SendNeig>();
		int ThreadNum = 0;
		Thread[] PacketDealThread =  new Thread[1000];
						
		
		if (SelfPort>=1024 && SelfPort<=65534) {
			Socket = new DatagramSocket(SelfPort);
			Socket1 = new DatagramSocket();
			if (args[args.length-1].equals("last")) {
				LastNode = true;
			}	//end if
			
			int SendPosition = 0;
			for (int i=1; i<args.length; i++) {		//get command send position
				if (args[i].equals("send")) {
					SendPosition = i;
				}	
			}	//end for
			
			RouteTable1.add(new RouteTable(SelfPort, 0, SelfPort));
			for (int i=2; i<SendPosition; i=i+2) {	//create receive neighbor list
				if (Integer.parseInt(args[i])>=1024 && Integer.parseInt(args[i])<=65534) {
					ReceiveNeigList1.add(new ReceiveNeig(Integer.parseInt(args[i]), Float.parseFloat(args[i+1])));
					NeigList1.add(new Neig(Integer.parseInt(args[i])));
					FormerNeigList1.add(new Neig(Integer.parseInt(args[i])));
					RouteTable1.add(new RouteTable(Integer.parseInt(args[i]), 0, Integer.parseInt(args[i])));
					TimeStamp1.add(new TimeStamp(Integer.parseInt(args[i])));
				}	//end if
				else {System.out.println("Port number is out of range(1024 ~ 65534)"); return;} 
			}	//end for

			//create send neighbor list
			int tempSendingBufferNum=0;
			for (int i=SendPosition+1; (i<args.length && !args[i].equals("last")); i++) {
				if (Integer.parseInt(args[i])>=1024 && Integer.parseInt(args[i])<=65534) {
					SendingBufferList1.add(new SendingBuffer(Socket1, IPAddress, Integer.parseInt(args[i]), NeigList1));
					SendingBufferList1.get(tempSendingBufferNum).SBuffer = SendingBufferList1.get(tempSendingBufferNum);
					tempSendingBufferNum++;
					SendNeigList1.add(new SendNeig(Integer.parseInt(args[i]), (SendingBufferList1.size()-1)));
					NeigList1.add(new Neig(Integer.parseInt(args[i])));
					FormerNeigList1.add(new Neig(Integer.parseInt(args[i])));
					RouteTable1.add(new RouteTable(Integer.parseInt(args[i]), 0, Integer.parseInt(args[i])));
					TimeStamp1.add(new TimeStamp(Integer.parseInt(args[i])));
				}	//end if
				else {System.out.println("Port number is out of range(1024 ~ 65534)"); return;} 
			}	//end for

			PrintRouteTable(RouteTable1);	//initial routing table
			
			if (LastNode) {		//last node start
				for (int i=0; i<SendingBufferList1.size(); i++) {
					SendingBufferList1.get(i).Send();
				}	//end for
				BroadCastTable(TimeStamp1, RouteTable1);
				Timer UpdateLossRateTimer = new Timer();
				UpdateLossRateTimer.schedule(new UpdateLossRate(), 200, 1000);
				Timer UpdateRouteTimer = new Timer();
				UpdateRouteTimer.schedule(new UpdateRoutePeriod(),500, 5000);
			}	//end if
			else {		//not last node
				byte[] receiveData = new byte[128];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					Socket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				for (int i=0; i<SendingBufferList1.size(); i++) {
					SendingBufferList1.get(i).Send();
				}	//end for
				PacketDealThread[ThreadNum] = new Thread(new DVUpdate(receivePacket, TimeStamp1, RouteTable1, SelfPort, Socket1, IPAddress));
				PacketDealThread[ThreadNum].start();
				ThreadNum++;
				Timer UpdateLossRateTimer = new Timer();
				UpdateLossRateTimer.schedule(new UpdateLossRate(), 200, 1000);
				Timer UpdateRouteTimer = new Timer();
				UpdateRouteTimer.schedule(new UpdateRoutePeriod(),500, 5000);
			}	//end else
			
			byte[] receiveData = new byte[128];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			while(true) {	//listen for DV packet
				try {
					Socket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}

				//throw incoming packets to a new thread
				PacketDealThread[ThreadNum] =  new Thread(new DVUpdate(receivePacket, TimeStamp1, RouteTable1, SelfPort, Socket1, IPAddress));
				PacketDealThread[ThreadNum].start();
				if (ThreadNum!=999) {
					ThreadNum++;
				}else ThreadNum = 0;
			}	//end while 
		}	else {System.out.println("Port number is out of range(1024 ~ 65534)");}

	}	//end main
	
	public static void BroadCastTable(ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table) throws IOException {
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] sendData = new byte[128];
		String SendInfo = "r " + Integer.toString(SelfPort) + " " + Float.toString(System.currentTimeMillis());
		for (int i=0; i<table.size(); i++) {
			SendInfo = SendInfo + " " + Integer.toString(table.get(i).GetDestPort()) + " " + Float.toString(table.get(i).GetDist());
		}	//end for
		sendData = SendInfo.getBytes();
		for (int i=0; i<timestamp.size(); i++) {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, timestamp.get(i).GetNeigPort());
			Socket1.send(sendPacket);
			System.out.println("[" + System.currentTimeMillis() + "] Message sent from Node " + Integer.toString(SelfPort) + " to Node " + Integer.toString(timestamp.get(i).GetNeigPort()));
		}
	}	//end method
	
	public static void PrintRouteTable(ArrayList<RouteTable> table) {
		System.out.println("["+System.currentTimeMillis()+"] Node " + SelfPort + " Routing Table");
		for (int i=1; i<table.size(); i++) {
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
}	//end class dvnode

//thread to deal incoming packets
class DVUpdate implements Runnable {
	String RcvInfo;
	DatagramPacket receivePacket;
	ArrayList<TimeStamp> TimeStamp;
	ArrayList<RouteTable> Table;
	static int SelfPort;
	static DatagramSocket Socket1;
	static InetAddress IP;
	
	public DVUpdate(DatagramPacket p, ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table, int port, DatagramSocket socket, InetAddress ip) {
		receivePacket = p;
		TimeStamp = timestamp;
		Table = table;
		SelfPort = port;
		Socket1 = socket;
		IP = ip;
	}	//end constructor
	
	public void run() {
		try {
			Thread.sleep(0);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		RcvInfo = DataToString(receivePacket);
		boolean IsUpdate = false;
		String[] rcvinfo = RcvInfo.split(" ");

		//receive route table
		if (rcvinfo[0].equals("r")) {
			System.out.println("[" + System.currentTimeMillis() + "] Message received at Node " + Integer.toString(SelfPort) + " from Node " + rcvinfo[1]);
			//get new weight of link
			float w = 100;
			for (int i=0; i<cnnode.FormerNeigList1.size(); i++) {
				if (cnnode.FormerNeigList1.get(i).GetNeigPort() == Integer.parseInt(rcvinfo[1])) {
					w = (float)(cnnode.FormerNeigList1.get(i).GetSendNum()-cnnode.FormerNeigList1.get(i).GetReceiveNum())/cnnode.FormerNeigList1.get(i).GetSendNum();
				}	//end if
			}	//end if 
			
			//Bellman-Ford alg
			for (int i=0; (2*i+4)<rcvinfo.length; i++) {	//update with new link weight
				for (int j=0; j<Table.size(); j++) {
					if (Table.get(j).GetDestPort() == Integer.parseInt(rcvinfo[2*i+3]) && Table.get(j).GetNextHop() == Integer.parseInt(rcvinfo[1]) && !rcvinfo[2*i+4].equals("NaN")) {
						Table.get(j).SetDist((w + Float.parseFloat(rcvinfo[2*i+4])));
						//IsUpdate = true;
					}	//end if
				}	//end for
			}	//end for 
			
			//Bellman-Ford alg
			for (int i=0; (2*i+4)<rcvinfo.length; i++) {	//relax operation
				boolean RouteExist = false;
				for (int j=0; j<Table.size(); j++) {
					if (Table.get(j).GetDestPort() == Integer.parseInt(rcvinfo[2*i+3]) && Table.get(j).GetDist()>(w + Float.parseFloat(rcvinfo[2*i+4])) && Table.get(j).GetNextHop() != Integer.parseInt(rcvinfo[1]) && !rcvinfo[2*i+4].equals("NaN")) {
						Table.get(j).SetDist((w + Float.parseFloat(rcvinfo[2*i+4])));
						Table.get(j).SetNextHop(Integer.parseInt(rcvinfo[1]));
						IsUpdate = true;
						RouteExist = true;
					}	//end if
					else if (Table.get(j).GetDestPort() == Integer.parseInt(rcvinfo[2*i+3]) && Table.get(j).GetDist()<=(w + Float.parseFloat(rcvinfo[2*i+4]))) {
						RouteExist = true;
					}	//end else if
				}
				if (!RouteExist && Integer.parseInt(rcvinfo[2*i+3])!=SelfPort && !rcvinfo[2*i+4].equals("NaN")) {
					Table.add(new RouteTable(Integer.parseInt(rcvinfo[2*i+3]), (w + Float.parseFloat(rcvinfo[2*i+4])), Integer.parseInt(rcvinfo[1])));
					IsUpdate = true;
				}	//end if
			}	//end for
	
			if (IsUpdate || cnnode.IsFirst) {
				try {
					BroadCastTable(TimeStamp, Table);
					cnnode.IsFirst = false;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}	//end if
		}	//end if 
		
		//receive probe
		else if (rcvinfo[0].equals("p")) {
			int tempPort = Integer.parseInt(rcvinfo[1]);
			float tempValueOfN = 0;
			int tempNeig = 0;
			for (int i=0; i<cnnode.ReceiveNeigList1.size(); i++) {
				if (cnnode.ReceiveNeigList1.get(i).NeigPort == tempPort) {
					tempValueOfN = cnnode.ReceiveNeigList1.get(i).ValueOfN;
					tempNeig = i;
				}
			}
			
			float p = (float) Math.random();
			if (p>tempValueOfN) {	//do not drop
				if (Integer.parseInt(rcvinfo[2])==cnnode.ReceiveNeigList1.get(tempNeig).AckNum) {
					cnnode.ReceiveNeigList1.get(tempNeig).AckNum++;
				}	//end if
				for (int i=0; i<cnnode.NeigList1.size(); i++) { 	//receive loss rate
					if (Integer.parseInt(rcvinfo[1])==cnnode.NeigList1.get(i).GetNeigPort()) {
						cnnode.NeigList1.get(i).SetSendNum(Integer.parseInt(rcvinfo[3]));
						cnnode.NeigList1.get(i).SetReceiveNum(Integer.parseInt(rcvinfo[4]));
					}	//end if
				}	//end for
				byte[] sendData = new byte[128];
				String Data = "a " + Integer.toString(cnnode.SelfPort) + " " + Integer.toString(cnnode.ReceiveNeigList1.get(tempNeig).AckNum-1);
				sendData = Data.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, Integer.parseInt(rcvinfo[1]));
				try {
					Socket1.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}	//end if
			else {
				return;
			}	//end else
		}	//end else if
		
		// receive ack
		else if (rcvinfo[0].equals("a")) {
			for (int i=0; i<cnnode.SendingBufferList1.size(); i++) {
				if (cnnode.SendingBufferList1.get(i).Port == Integer.parseInt(rcvinfo[1])) {
					try {
						cnnode.SendingBufferList1.get(i).Ack(Integer.parseInt(rcvinfo[2]));
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}	//end if
			}	//end for
		}		//end else if
	}	//end run
	
	static synchronized String DataToString(DatagramPacket packet) {	//remove useless space
		byte[] buffer = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, buffer, 0, packet.getLength());
		String ReceivedInfo = new String(buffer);	
		return ReceivedInfo;
	}	//end method
	
	public static synchronized void BroadCastTable(ArrayList<TimeStamp> timestamp, ArrayList<RouteTable> table) throws IOException  {
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] sendData = new byte[128];
		String SendInfo = "r " + Integer.toString(SelfPort) + " " + Float.toString(System.currentTimeMillis());
		for (int i=0; i<table.size(); i++) {
			SendInfo = SendInfo + " " + Integer.toString(table.get(i).GetDestPort()) + " " + Float.toString(table.get(i).GetDist());
		}	//end for
		sendData = SendInfo.getBytes();
		for (int i=0; i<timestamp.size(); i++) {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, timestamp.get(i).GetNeigPort());
			Socket1.send(sendPacket);
			System.out.println("[" + System.currentTimeMillis() + "] Message sent from Node " + Integer.toString(SelfPort) + " to Node " + Integer.toString(timestamp.get(i).GetNeigPort()));
		}
	}	//end method
	
	public static synchronized void PrintRouteTable(ArrayList<RouteTable> table) {
		System.out.println("["+System.currentTimeMillis()+"] Node " + SelfPort + " Routing Table");
		for (int i=1; i<table.size(); i++) {
			System.out.print("- ("); 
			System.out.printf("%.1f",table.get(i).GetDist());
			System.out.print(") -> Node " + Integer.toString(table.get(i).GetDestPort()));
			if (table.get(i).GetDestPort() == table.get(i).GetNextHop()) {
				//System.out.print("\n");
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
}	//end class TimeStamp

class ReceiveNeig {
	public int NeigPort;
	public float ValueOfN;
	public int AckNum = 0;
	
	public ReceiveNeig(int port, float f) {
		NeigPort = port;
		ValueOfN = f;
	}	//end constructor
}	//end class LossRate

class SendNeig {
	private int NeigPort;
	private int BufferNum;
	
	public SendNeig(int port, int n) {
		NeigPort = port;
		BufferNum = n;
	}	//end constructor
}	//end class LossRate

class Neig {
	private int NeigPort;
	private int SendNum;
	private int ReceiveNum;
	
	public Neig(int port) {
		NeigPort = port;
		SendNum = 1;
		ReceiveNum = 1;
	}	//end constructor
	
	public int GetNeigPort() {
		return NeigPort;
	}	//end method
	
	public int GetSendNum() {
		return SendNum;
	}	//end method
	
	public int GetReceiveNum() {
		return ReceiveNum;
	}	//end method
	
	public void SetSendNum(int n) {
		SendNum = n;
	}	//end method
	
	public void SetReceiveNum(int n) {
		ReceiveNum = n;
	}	//end method
}	//end class Neig


class SendingBuffer {
	DatagramSocket Socket1;
	InetAddress IP;
	int Port;
	private int BufferLength;
	String[] Buffer;
	public boolean[] IsSent;
	public int Window;
	private int WindowSize;
	int SeqNow;
	private Timer ResendTimer;
	public SendingBuffer SBuffer;
	ArrayList<Neig> NeigList1;
	private int SendNum;
	private int ReceiveNum;
		
	public SendingBuffer(DatagramSocket socket, InetAddress ip, int port, ArrayList<Neig> neiglist) {
		BufferLength = 100000;
		SeqNow = 0;
		IsSent = new boolean[BufferLength];
		Buffer = new String[BufferLength];
		Socket1 = socket;
		IP = ip;
		Port = port;
		for (int i=0; i<BufferLength; i++) {
			IsSent[i] = false;
			Buffer[i] = new String();
		}	//end for
		Window = 0;
		WindowSize = 5;
		NeigList1 = neiglist;
	}	//end constructor
	
	public synchronized void Send() throws IOException, InterruptedException {
		int w=Window;
		if (IsSent[w%BufferLength]) {
			ResendTimer = new Timer();
			ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
		}	//end if
		for (int i=0; i<WindowSize; i++) {
			if (!IsSent[(w+i)%BufferLength]) {
				String sendInfo = "p " + Integer.toString(cnnode.SelfPort) + " " +Integer.toString(SeqNow+i) + " " + Integer.toString(SendNum) + " " + Integer.toString(ReceiveNum);
				byte[] sendData = new byte[128];
				sendData = sendInfo.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, Port);
				Socket1.send(sendPacket);
				SendNum++;
				for (int j=0; j<NeigList1.size(); j++) {
					if (NeigList1.get(j).GetNeigPort() == Port) {
						NeigList1.get(j).SetSendNum(SendNum);
					}	//end if
				}	//end for 
				if (i == 0) {
					ResendTimer = new Timer();
					ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
				}	//end if
				IsSent[(w+i)%BufferLength] = true;
				String sendstr = DataToString(sendPacket);
				String[] sendstrs = sendstr.split(" ");
				Thread.sleep((long) (10*Math.random()+10));
			}
		}	//end for
	}	//end method
	
	public synchronized void Resend() throws IOException, InterruptedException {
		int w=Window;
		if (IsSent[w%BufferLength]) {
			ResendTimer = new Timer();
			ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
		}	//end if
		for (int i=0; i<WindowSize; i++) {
			String sendInfo = "p " + Integer.toString(cnnode.SelfPort) + " " + Integer.toString(SeqNow+i) + " " + Integer.toString(SendNum) + " " + Integer.toString(ReceiveNum);
			byte[] sendData = new byte[128];
			sendData = sendInfo.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, Port);
			Socket1.send(sendPacket);
			SendNum++;
			for (int j=0; j<NeigList1.size(); j++) {
				if (NeigList1.get(j).GetNeigPort() == Port) {
					NeigList1.get(j).SetSendNum(SendNum);
				}	//end if
			}	//end for 
			IsSent[(w+i)%BufferLength] = true;
			String sendstr = DataToString(sendPacket);
			String[] sendstrs = sendstr.split(" ");
			//System.out.println("["+System.currentTimeMillis()+"] packet"+sendstrs[2]+ " resent");
			Thread.sleep((long) (10*Math.random()+10));

		}	//end for
	}	//end method
	
	public void MoveWindow(int offset) {
		Window = Window + offset;
		if (Window>=BufferLength) {
			Window = Window - BufferLength;
		}	//end if
	}	//end method
	
	public synchronized void Ack(int num) throws IOException, InterruptedException {
		int w = Window;
		int tempSeq = SeqNow;
		ReceiveNum++;
		for (int i=0; i<NeigList1.size(); i++) {
			if (NeigList1.get(i).GetNeigPort() == Port) {
				NeigList1.get(i).SetReceiveNum(ReceiveNum);
			}	//end if
		}	//end for 
		for (int i=0; i<WindowSize; i++) {
			if (tempSeq+i<num) {
				MoveWindow(1);
				SeqNow++;
				ResendTimer.cancel();
			}	//end if
			else if ((tempSeq+i)==num) {
				MoveWindow(1);
				SeqNow++;
				ResendTimer.cancel();
				Send();
				return;
			}	//end if
			else {
				return;
			}	//end else
		}
	}	//end method
	
   String DataToString(DatagramPacket packet) {	//remove useless space
		byte[] buffer = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, buffer, 0, packet.getLength());
		String ReceivedInfo = new String(buffer);	
		return ReceivedInfo;
	}	//end method
}

class TimeoutResend extends TimerTask{	//gbn timer
    SendingBuffer Buffer;
    
    public TimeoutResend(SendingBuffer buffer)  {
    	Buffer = buffer;
    }	//constructor
    
	public void run() {  
		try {
			Buffer.Resend();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}  
}  

class UpdateLossRate extends TimerTask{		//update link weight every second
	public synchronized void run() {
		
		for (int i=0; i<cnnode.NeigList1.size(); i++) {
			cnnode.FormerNeigList1.get(i).SetSendNum(cnnode.NeigList1.get(i).GetSendNum());
			cnnode.FormerNeigList1.get(i).SetReceiveNum(cnnode.NeigList1.get(i).GetReceiveNum());
			System.out.print("["+System.currentTimeMillis()+"] Link to Node "+ cnnode.FormerNeigList1.get(i).GetNeigPort() +": " + cnnode.FormerNeigList1.get(i).GetSendNum() + " packets sent, " + (cnnode.FormerNeigList1.get(i).GetSendNum()-cnnode.FormerNeigList1.get(i).GetReceiveNum()) + " packets lost, loss rate ");
			System.out.printf("%.2f\n", (float)(cnnode.FormerNeigList1.get(i).GetSendNum()-cnnode.FormerNeigList1.get(i).GetReceiveNum())/cnnode.FormerNeigList1.get(i).GetSendNum());
		}
	}	//end run
}	//end class

class UpdateRoutePeriod extends TimerTask{		//update route table every 5 seconds
	public synchronized void run() {
		try {
			cnnode.BroadCastTable(cnnode.TimeStamp1,cnnode.RouteTable1);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		cnnode.PrintRouteTable(cnnode.RouteTable1);
	}	//end run
}	//end class






	