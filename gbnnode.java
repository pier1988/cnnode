import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class gbnnode {
	public static void main(String args[]) throws IOException, InterruptedException {
		InetAddress IPAddress = InetAddress.getByName("localhost");
		int SelfPort = Integer.parseInt(args[0]);
		int PeerPort = Integer.parseInt(args[1]);
		
		if (SelfPort>=1024 && SelfPort<=65534 && PeerPort>=1024 && PeerPort<=65534) {
			DatagramSocket Socket = new DatagramSocket(SelfPort);
			int WindowSize = Integer.parseInt(args[2]);
			String Mode = args[3];
			float ValueOfN = Float.parseFloat(args[4]);
			if (Mode.equals("-d") || Mode.equals("-p")) {
				SendingBuffer SendingBuffer1 = new SendingBuffer(WindowSize, Socket, IPAddress, PeerPort);
				int AckNum = 0;
				Thread Listener1 = new Thread(new PacketListener(Socket, IPAddress, SelfPort, PeerPort, SendingBuffer1, AckNum, Mode, ValueOfN));
				Listener1.start();
				System.out.print("node>");
				
				while (true) {
					
					//read command from user
					BufferedReader InputFromUser = new BufferedReader(new InputStreamReader(System.in));
					String str = InputFromUser.readLine();
					String[] UserComm = str.split(" ");
					if (UserComm[0].equals("send")) {
						String Message = UserComm[1];
							
						for (int i=0; i<Message.length(); i++) {	//put message into buffer
							SendingBuffer1.MakePacket(Message.charAt(i));
						}
						
						SendingBuffer1.SBuffer = SendingBuffer1;
						SendingBuffer1.Send();
							
					
					} else {System.out.println("wrong command!");}	
				}	//end while
				
			} {System.out.println("wrong command.");}
		}	else {System.out.println("Port number is out of range(1024 ~ 65534)");}
		
	}	//end main
	
}

//thread to listen incoming port
class PacketListener implements Runnable {
	private DatagramSocket Socket;
	private InetAddress IP;
	private int SelfPort;
	private int PeerPort;
	private SendingBuffer Buffer;
	private int AckNum;
	private String Mode;
	private int DropCount;
	private float ValueOfN;
	
	
	public PacketListener(DatagramSocket sk, InetAddress ip, int port, int pport, SendingBuffer buffer, int acknum, String mode, float von) {
		Socket = sk;
		IP = ip;
		SelfPort = port;
		PeerPort = pport;
		Buffer = buffer;
		AckNum = acknum;
		Mode = mode;
		DropCount = 1;
		ValueOfN = von;
	}	//end constructor
	
	public void run() {
		while(true) {
			byte[] receiveData = new byte[64];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				Socket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
			}

			String ReceivedData = DataToString(receivePacket);
			String[] ReceivedMessage = ReceivedData.split(" ");
			
			//sender
			if (ReceivedMessage.length == 1) {	//receive ack message
				if (Mode.equals("-d")) {
					if (DropCount%ValueOfN!=0) {	//do not drop		
						try {
							Buffer.Ack(Integer.parseInt(ReceivedMessage[0]));
						} catch (IOException e) {
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}	//end if
					else {
						System.out.println("["+System.currentTimeMillis()+"] ACK"+ReceivedMessage[0]+" discarded");
					}
					DropCount++;
				}	//end if
				else if (Mode.equals("-p")) {
					float p = (float) Math.random();
					if (p>ValueOfN) {	//do not drop		
						try {
							Buffer.Ack(Integer.parseInt(ReceivedMessage[0]));
						} catch (IOException e) {
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}	//end if
					else {
						System.out.println("["+System.currentTimeMillis()+"] ACK"+ReceivedMessage[0]+" discarded");
					}
				}	//end else if
			}	//end if
			
			//receiver
			else if (ReceivedMessage.length == 2) {	//right order message
				if (Mode.equals("-d")) {
					if (DropCount%ValueOfN!=0) {	//do not drop				
						System.out.println("["+System.currentTimeMillis()+"] packet"+ReceivedMessage[0]+" "+ReceivedMessage[1] + " received");
						if (Integer.parseInt(ReceivedMessage[0])==AckNum) {
							AckNum++;
						}	//end if
						byte[] sendData = new byte[64];
						String Data = Integer.toString(AckNum-1);
						sendData = Data.getBytes();
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, PeerPort);
						try {
							Socket.send(sendPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("["+System.currentTimeMillis()+"] ACK"+Integer.toString(AckNum-1)+" sent, expecting packet"+Integer.toString(AckNum));
					}	//end if
					else {
						System.out.println("["+System.currentTimeMillis()+"] packet"+ReceivedMessage[0]+" "+ReceivedMessage[1] + " discarded");
					}	//end else
					DropCount++;
				}	//end if
				else if (Mode.equals("-p")) {
					float p = (float) Math.random();
					if (p>ValueOfN) {	//do not drop
						System.out.println("["+System.currentTimeMillis()+"] packet"+ReceivedMessage[0]+" "+ReceivedMessage[1] + " received");
						if (Integer.parseInt(ReceivedMessage[0])==AckNum) {
							AckNum++;
						}	//end if
						byte[] sendData = new byte[64];
						String Data = Integer.toString(AckNum-1);
						sendData = Data.getBytes();
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, PeerPort);
						try {
							Socket.send(sendPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("["+System.currentTimeMillis()+"] ACK"+Integer.toString(AckNum-1)+" sent, expecting packet"+Integer.toString(AckNum));
					}	//end if
					else {
						System.out.println("["+System.currentTimeMillis()+"] packet"+ReceivedMessage[0]+" "+ReceivedMessage[1] + " discarded");
					}	//end else
				}	//end if
			}	//end else if
		}	//end while
	}	//end method
	
	String DataToString(DatagramPacket packet) {	//remove useless space
		byte[] buffer = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, buffer, 0, packet.getLength());
		String ReceivedInfo = new String(buffer);	
		return ReceivedInfo;
	}	//end method
}	//end Listener

class SendingBuffer {
	DatagramSocket Socket;
	InetAddress IP;
	int Port;
	private int BufferLength;
	String[] Buffer;
	//public byte[][] Buffer;
	public boolean[] IsEmpty;
	public boolean[] IsSent;
	public int[] SeqNum;
	public int Window;
	private int WindowSize;
	private int MessageSize;
	private int SeqNow;
	public Timer ResendTimer;
	public TimeoutResend Resend;
	public SendingBuffer SBuffer;
		
	public SendingBuffer(int size, DatagramSocket socket, InetAddress ip, int port) {
		BufferLength = 100;
		SeqNow = 0;
		//Buffer = new byte[BufferLength][5];
		IsEmpty = new boolean[BufferLength];
		IsSent = new boolean[BufferLength];
		SeqNum = new int[BufferLength];
		Buffer = new String[BufferLength];
		Socket = socket;
		IP = ip;
		Port = port;
		for (int i=0; i<BufferLength; i++) {
			IsEmpty[i] = true;
			IsSent[i] = false;
			SeqNum[i] = -1;
			Buffer[i] = new String();
		}	//end for
		Window = 0;
		MessageSize = 0;
		WindowSize = size;
	}	//end constructor
	
	public void MakePacket(char c) {
		Buffer[MessageSize] = Integer.toString(SeqNow) + " " + c;
		IsEmpty[MessageSize] = false;
		IsSent[MessageSize] = false;
		SeqNum[MessageSize] = SeqNow;
		SeqNow++;
		MoveMessageSize(1);
	}	//end method
	
	public synchronized void Send() throws IOException, InterruptedException {
		int w=Window;
		if (IsSent[w%BufferLength]) {
			ResendTimer = new Timer();
			ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
		}	//end if
		for (int i=0; i<WindowSize; i++) {
			if (!IsEmpty[(w+i)%BufferLength] && !IsSent[(w+i)%BufferLength]) {
				byte[] sendData = new byte[64];
				sendData = Buffer[(w+i)%BufferLength].getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, Port);
				Socket.send(sendPacket);
				if (i == 0) {
					ResendTimer = new Timer();
					ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
				}	//end if
				IsSent[(w+i)%BufferLength] = true;
				String sendstr = DataToString(sendPacket);
				String[] sendstrs = sendstr.split(" ");
				System.out.println("["+System.currentTimeMillis()+"] packet"+sendstrs[0]+" "+sendstrs[1] + " sent");
				Thread.sleep((long) (10*Math.random()+10));
			}
		}	//end for
	}	//end method
	
	public synchronized void Resend() throws IOException, InterruptedException {
		int w=Window;
		if (IsSent[w%BufferLength]) {
			//ResendTimer = new Timer();
			ResendTimer.schedule(new TimeoutResend(SBuffer), 500);
		}	//end if
		for (int i=0; i<WindowSize; i++) {
			if (!IsEmpty[(w+i)%BufferLength]) {
				byte[] sendData = new byte[64];
				sendData = Buffer[(w+i)%BufferLength].getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IP, Port);
				Socket.send(sendPacket);
				IsSent[(w+i)%BufferLength] = true;
				String sendstr = DataToString(sendPacket);
				String[] sendstrs = sendstr.split(" ");
				System.out.println("["+System.currentTimeMillis()+"] packet"+sendstrs[0]+" "+sendstrs[1] + " sent");
				Thread.sleep((long) (10*Math.random()+10));
			}
		}	//end for
	}	//end method
	
	public void MoveWindow(int offset) {
		Window = Window + offset;
		if (Window>=BufferLength) {
			Window = Window - BufferLength;
		}	//end if
	}	//end method
	
	public void MoveMessageSize(int offset) {
		MessageSize = MessageSize + offset;
		if (MessageSize>=BufferLength) {
			MessageSize = MessageSize - BufferLength;
		}	//end if
	}	//end method
	
	public void Ack(int num) throws IOException, InterruptedException {
		int w = Window;
		for (int i=0; i<WindowSize; i++) {
			if (SeqNum[(w+i)%BufferLength]<num) {
				IsEmpty[(w+i)%BufferLength] = true;
				MoveWindow(1);
				ResendTimer.cancel();
			}	//end if
			else if (SeqNum[(w+i)%BufferLength]==num) {
				IsEmpty[(w+i)%BufferLength] = true;
				MoveWindow(1);
				ResendTimer.cancel();
				System.out.println("["+System.currentTimeMillis()+"] ACK"+num+" received, window moves to "+((num+1)%BufferLength));
				Send();
				return;
			}	//end if
			else {
				System.out.println("["+System.currentTimeMillis()+"] ACK"+num+" received, window moves to "+Integer.toString(Window));
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

class TimeoutResend extends TimerTask{  
    SendingBuffer Buffer;
    
    public TimeoutResend(SendingBuffer buffer)  {
    	Buffer = buffer;
    }	//constructor
    
	public void run() {  
		System.out.println("["+System.currentTimeMillis()+"] packet"+Buffer.SeqNum[Buffer.Window]+" timeout");
		try {
			Buffer.Resend();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}  
}  
