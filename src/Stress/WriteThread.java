/***********************************Write Request Thread *************************************\\
1. Send Requests on DataOutputStream on each socket as per the defined communication mechanism: Persistent, Transient or Socket_Pool
2. For each request, 4 byte function identifier is added after function name(function name assumed to be 3 bytes) as defined below:
	Function Identifier(4 bytes) = Function Count(2 bytes) + Thread_ID(2 bytes)
3. Function Count(msg_id) iterated from 0 to 9999 for Luna EFT host functions while for Thales functions, Function count iterates from 0 to 99.
3. Stores Requests sent into the Buffer for validation by Read Thread
\\******************************************************************************************/
package Stress;

import java.util.Arrays;
import java.util.Date;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

import edu.emory.mathcs.util.net.Connection;

public class WriteThread extends Thread {
	private int msg_id = 0;
	private int amb_msg_id = 0;
	private int Thread_id = 0;

	Thread wt;
	private String thread_name;
	private DataOutputStream buffered_dataout;
	private DataInputStream buffered_datain;
	private BufferedWriter filelogger = null;
	private String request_list[];
	private String response_list[];
	private int request_loop = 0;
	private int request_index = 0;
	private boolean Debug = false;
	private int request_per_thread = 0;
	private int request_count = 0;
	private String mechanism = "";
	private Socket socketfrompool;
	private String protocol = "";
	private String msg = "";
	String expectedResponse = ""; // Rajat added 20.07.2016

	WriteThread(int id, String request[], int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);

			if (Connections.secure_channel) {

				synchronized (Connections.SSLSockets[Thread_id]) {

					request_list = new String[request_index];

					this.request_index = request_index;
					Debug = Connections.Debug;
					mechanism = Connections.mechanism;
					protocol = Connections.protocol;

					if (mechanism.equals("Transient"))
						request_per_thread = Connections.request_per_thread;

					for (int i = 0; i < request_index; i++) {
						request_list[i] = new String(request[i]);
					}

					buffered_dataout = new DataOutputStream(
							Connections.SSLSockets[Thread_id].getOutputStream());

					// if (Connections.secure_channel)
					getFileHandle();
				}
			} else {
				synchronized (Connections.Sockets[Thread_id]) {

					request_list = new String[request_index];

					this.request_index = request_index;
					Debug = Connections.Debug;
					mechanism = Connections.mechanism;
					protocol = Connections.protocol;

					if (mechanism.equals("Transient"))
						request_per_thread = Connections.request_per_thread;

					for (int i = 0; i < request_index; i++) {
						request_list[i] = new String(request[i]);
					}

					buffered_dataout = new DataOutputStream(
							Connections.Sockets[Thread_id].getOutputStream());

					// getFileHandle();//RJK

				}

			}
		} catch (Exception e) {
			if (Debug)
				System.out
						.println("WriteThread ERROR.init : " + e.getMessage());
		}
		wt = new Thread(this, thread_name);

		wt.start(); // Start the thread
	}

	WriteThread(int id, String request[], String response[], int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);

			if (Connections.secure_channel) {

				synchronized (Connections.SSLSockets[Thread_id]) {

					request_list = new String[request_index];
					response_list = new String[request_index];

					this.request_index = request_index;
					Debug = Connections.Debug;
					mechanism = Connections.mechanism;
					protocol = Connections.protocol;

					if (mechanism.equals("Transient"))
						request_per_thread = Connections.request_per_thread;

					for (int i = 0; i < request_index; i++) {
						request_list[i] = new String(request[i]);
						response_list[i] = new String(response[i]);
					}

					// Connections.SSLSockets[Thread_id].setSoTimeout(Connections.soc_timeout);

					buffered_dataout = new DataOutputStream(
							Connections.SSLSockets[Thread_id].getOutputStream());

					getFileHandle();
				}
			} else {
				synchronized (Connections.Sockets[Thread_id]) {

					request_list = new String[request_index];
					response_list = new String[request_index];

					this.request_index = request_index;
					Debug = Connections.Debug;
					mechanism = Connections.mechanism;
					protocol = Connections.protocol;

					if (mechanism.equals("Transient"))
						request_per_thread = Connections.request_per_thread;

					for (int i = 0; i < request_index; i++) {
						request_list[i] = new String(request[i]);
						response_list[i] = new String(response[i]);
					}

					// Connections.Sockets[Thread_id].setSoTimeout(Connections.soc_timeout);

					buffered_dataout = new DataOutputStream(
							Connections.Sockets[Thread_id].getOutputStream());

					// getFileHandle();//RJK
				}
			}
		} catch (Exception e) {
			if (Debug)
				System.out
						.println("WriteThread ERROR.init : " + e.getMessage());
		}
		wt = new Thread(this, thread_name);

		wt.start(); // Start the thread
	}

	WriteThread(int id, Socket socket, Connection conn, String request[],
			int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);
			socketfrompool = socket;
			protocol = Connections.protocol;

			synchronized (socketfrompool) {
				request_list = new String[request_index];
				this.request_index = request_index;
				Debug = Connections.Debug;
				mechanism = Connections.mechanism;
				request_per_thread = Connections.request_per_thread;

				for (int i = 0; i < request_index; i++) {
					request_list[i] = new String(request[i]);
				}

				buffered_dataout = new DataOutputStream(
						socket.getOutputStream());
			}
		} catch (Exception e) {
			if (Debug)
				System.out
						.println("WriteThread ERROR.init : " + e.getMessage());
		}
		wt = new Thread(this, thread_name);

		wt.start(); // Start the thread
	}

	/*
	 * Different Stress Mechanisms implemented for Writing Request to
	 * OutputStream
	 */
	public void run() {
		msg = "";
		// String request_temp = "";

		switch (mechanism) {
		case "Persistent": /* Persistent Connections */

			try {
				while (true) 
				{
					if (request_loop < request_index) {
						/* Request String prepared for Luna EFT Host functions */
						if (protocol.equals("PROT_SOH")) {

							if (prepareLunaEFTRequest()) {
								byte[] f = convertStringToHex(msg.trim());
								sendrequest(f);

								msg_id++;

								/* Message id ranges from 0 to 99999 */
								if (msg_id > (Connections.max_buffer_size - 1))// 99999
									msg_id = 0;
							}

						} else if (protocol.equals("PROT_RAC"))// &&
																// (!Connections.thalespacketsent))
																// /*Request
																// String
																// prepared for
																// Thales Host
																// functions*/
						{
							if (prepareTEPRequest()) {
								/* Converting Request String to ASCII format */
								byte[] f = msg.getBytes("US-ASCII");

								/* Send Request to OutputStream */
								sendrequest(f);

								msg_id++;

								/* Message Identifier varies from 0 to 99 */
								if (msg_id > 99)
									msg_id = 0;
							}
						}
					}

					request_loop++;

					if (request_loop >= request_index) 
					{
						request_loop = 0;

					}
					continue;
				}
			} catch (Exception e) {
				if (Debug) {
					System.out.println("Request: " + msg);
					System.out.println("ERROR.send.run : " + request_loop);
					e.printStackTrace();
				}
			} finally {
				break;
			}

		case "Transient": /* Transient Connections */

			try {
				Connections.start_transient_send[Thread_id] = true;
				while (true) {
					msg_id = 0;
					request_loop = 0;
					request_count = 0;

					if (Connections.start_transient_send[Thread_id]) 
					{
						while (request_count < request_per_thread) 
						{
							if (request_loop < request_index) 
							{
								/*
								 * Request String prepared for Luna EFT Host
								 * functions
								 */
								if (protocol.equals("PROT_SOH")) 
								{
									if (prepareLunaEFTRequest()) 
									{
										byte[] f = convertStringToHex(msg
												.trim());
										sendrequest(f);

										msg_id++;

										/* Message id ranges from 0 to 65535 */
										if (msg_id > (Connections.max_buffer_size - 1))
											msg_id = 0;
									}

								} else if (protocol.equals("PROT_RAC"))// &&
																		// (!Connections.thalespacketsent))/*Request
																		// String
																		// prepared
																		// for
																		// Thales
																		// Host
																		// functions*/
								{
									if (prepareTEPRequest()) {
										/*
										 * Converting Request String to ASCII
										 * format
										 */
										byte[] f = msg.getBytes("US-ASCII");

										/* Send Request to OutputStream */
										sendrequest(f);

										msg_id++;

										/*
										 * Message Identifier varies from 0 to
										 * 99
										 */
										if (msg_id > 99)
											msg_id = 0;
									}
								}

								request_loop++;
								request_count++;
							}

							if (request_loop >= request_index) {
								request_loop = 0;
							}
						}

						if (Connections.secure_channel) 
						{
							if (request_count >= request_per_thread) 
							{
								/*
								 * Socket is closed once all responses are
								 * received for this session
								 */

								//System.out.println(Connections.SSLSockets[Thread_id] +" Before Closure. State:"+Connections.SSLSockets[Thread_id].isClosed());
								Connections.SSLSockets[Thread_id].close();
								//System.out.println(Connections.SSLSockets[Thread_id] +" After Closure. State : "+Connections.SSLSockets[Thread_id].isClosed());
								
								Connections.connstatus[Thread_id] = false;

								/*
								 * Connection delay between closing of
								 * socket & re-opening socket
								 */
								Thread.sleep(Connections.delay);
							}

							/*
							 * Socket initialization called for transient
							 * mechanism where Socket is closed after each
							 * session
							 */
							if (Connections.initialize_Socket(
									Connections.hip, Connections.hport,
									Thread_id)) 
							{

								Connections.connstatus[Thread_id] = true;
								Connections.start_transient_send[Thread_id] = true;
								// if(Debug)
								// System.out.println("Thread: "+Thread_id+" Socket Opened : "+Connections.Sockets[Thread_id]+" Read thread restart : "+Connections.start_transient_send[Thread_id]);
							}

						}
						else{
							Connections.start_transient_send[Thread_id] = false;
						}
					
					// if(Debug)
					// System.out.println("WriteThread 1 stop :"+Connections.start_transient_send[Thread_id]+
					// " request_count"+request_count + " Socket ID : " +
					// Connections.Sockets[Thread_id]);

					continue;
				}
			}
		} catch (Exception e) {
			if (Debug) {
				System.out.println("ERROR.send.run : " + request_loop);
				e.printStackTrace();// .getMessage());
			}
		} finally {
			break;
		}

		case "Socket_Pool": /* Connection Pooling */

			try {
				while (request_count < request_per_thread) {
					if (request_loop < request_index) {
						/* Request String prepared for Luna EFT Host functions */
						if (protocol.equals("PROT_SOH")) {
							if (prepareLunaEFTRequest()) {
								byte[] f = convertStringToHex(msg.trim());
								sendrequest_pool(f);

								msg_id++;

								/* Message id ranges from 0 to 9999 */
								if (msg_id > (Connections.max_buffer_size - 1))
									msg_id = 0;
							}
						} else if (protocol.equals("PROT_RAC"))// &&
																// (!Connections.thalespacketsent))/*Request
																// String
																// prepared for
																// Thales Host
																// functions*/
						{
							if (prepareTEPRequest()) {
								/* Converting Request String to ASCII format */
								byte[] f = msg.getBytes("US-ASCII");

								/* Send Request to OutputStream */
								sendrequest_pool(f);

								msg_id++;

								/* Message Identifier varies from 0 to 99 */
								if (msg_id > 99)
									msg_id = 0;
							}

						}
					}
					request_loop++;
					request_count++;

					if (request_loop >= request_index) {
						request_loop = 0;
					}

					continue;
				}
			} catch (Exception e) {
				if (Debug) {
					System.out.println("ERROR.send.run : " + request_loop);
					e.printStackTrace();
				}
			} finally {
				break;
			}
		}
	}

	/*
	 * Prepares LunaEFT Request Message = Safenet Request Header + Request
	 * Message
	 */
	public boolean prepareLunaEFTRequest() throws Exception {
		String request_temp = "";
		/* Rajat edit starts here 20.07.2016 */
		String response_temp = "";
		expectedResponse = "";
		/* Rajat edit ends here 20.07.2016 */

		try {
			request_temp = request_list[request_loop];
			response_temp = response_list[request_loop];

			if (request_temp.substring(0, 2).equals("EE")
					|| request_temp.substring(0, 2).equals("EF") || request_temp.substring(0, 2).equals("EA")) {
				int itemp;
				int requestlength = Connections.Sfnt_Msg_Identifier_Length
						+ (request_temp.length() / 2);

				/* 1. Preparing 6 byte Request Header */
				byte[] Request_header = new byte[Connections.Sfnt_Msg_Header_Length];

				/* these bytes are header (2) + any info (2) */
				Request_header[0] = 0x01;
				Request_header[1] = 0x01;
				Request_header[2] = 0x12;
				Request_header[3] = 0x12;

				/* next two bytes are length */
				itemp = (requestlength) & 0x0000FFFF;
				byte cLowBit = (byte) itemp;

				itemp = (requestlength / 256);// & 0xFFFF0000;
				byte cHighBit = (byte) itemp;

				Request_header[4] = cHighBit;
				Request_header[5] = cLowBit;

				msg = convertHexToString(Request_header,
						Connections.Sfnt_Msg_Header_Length);

				/*
				 * 2. Appending 3 bytes of Function name to Request String
				 */
				msg += request_temp.substring(0, 6);
				expectedResponse += response_temp.substring(0, 6);

				/*
				 * 3. Appending 5 bytes function identifier (3 bytes message id
				 * and 2 bytes thread id)
				 */
				String tmp = "" + msg_id;

				int len = tmp.length();

				for (int i = 0; i < Connections.Sfnt_Msg_ID - len; i++)

					tmp = "0" + tmp;

				msg += tmp;
				expectedResponse += tmp;

				String str_thd_id = "" + Thread_id;
				len = str_thd_id.length();

				for (int i = 0; i < Connections.Sfnt_Thread_ID - len; i++) {
					str_thd_id = "0" + str_thd_id;
				}

				msg += str_thd_id;
				expectedResponse += str_thd_id;

				/*
				 * Appending remaining bytes of request to Request String
				 */
				msg += request_temp.substring(6);
				expectedResponse += response_temp.substring(6);

				// System.out.println("Send message: "+msg);
			} else {
				int itemp;
				int requestlength = request_temp.length() / 2;

				/* 1. Preparing 6 byte Request Header */
				byte[] Request_header = new byte[Connections.Sfnt_Msg_Header_Length];

				/* these bytes are header (2) + any info (2) */
				Request_header[0] = 0x01;
				Request_header[1] = 0x01;
				Request_header[2] = 0x12;
				Request_header[3] = 0x12;

				/* next two bytes are length */
				itemp = (requestlength) & 0x0000FFFF;
				byte cLowBit = (byte) itemp;

				itemp = (requestlength / 256);// & 0xFFFF0000;
				byte cHighBit = (byte) itemp;

				Request_header[4] = cHighBit;
				Request_header[5] = cLowBit;

				msg = convertHexToString(Request_header,
						Connections.Sfnt_Msg_Header_Length);
				/*
				 * 2. Appending 2 bytes of Function name to Request String
				 */
				msg += request_temp.substring(0, 4);
				expectedResponse += response_temp.substring(0, 4);

				/*
				 * 3. Appending 2 bytes function identifier (2 bytes thread id)
				 */

				String tmp = "" + Integer.toHexString(msg_id);
				int len = tmp.length();

				for (int i = 0; i < 4 - len; i++) {
					tmp = "0" + tmp;
				}

				msg += tmp;
				expectedResponse += tmp;

				/*
				 * Appending remaining bytes of request to Request String
				 */
				msg += request_temp.substring(8);
				expectedResponse += response_temp.substring(8);

				// System.out.println("Send message: "+msg);
			}

			return true;
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				e.printStackTrace();
			System.out.println("Thread: " + Thread_id
					+ " ERROR.PrepareLunaEFTRequest [" + date.toString()
					+ "] : " + e.getMessage());// " - Disconnected : ");// +//
												// Connections.hip);

			return false;
		}

	}

	/*
	 * Prepares TEP Request Message = TEP Request Header + Request Message
	 */
	public boolean prepareTEPRequest() throws Exception {
		String request_temp = "";

		try {

			request_temp = request_list[request_loop];

			/*
			 * Read & parse Request string for "x__" values to convert them to
			 * Ascii
			 */
			while (request_temp.indexOf("x") > -1) {
				String str_temp = request_temp.substring(
						(request_temp.indexOf("x") + 1),
						(request_temp.indexOf("x") + 3));
				String str_to_replace = request_temp.substring(
						request_temp.indexOf("x"),
						request_temp.indexOf("x") + 3);
				str_temp = convertStringToAscii(str_temp);
				request_temp = request_temp.replace(str_to_replace, str_temp);
			}

			int itemp;
			int requestlength = request_temp.length();

			/* 1. Preparing 2 byte Request Header */
			byte[] Request_header = new byte[2];

			itemp = (requestlength) & 0x0000FFFF;
			byte cLowBit = (byte) itemp;

			itemp = (requestlength) & 0xFFFF0000;
			byte cHighBit = (byte) itemp;

			Request_header[0] = cHighBit;
			Request_header[1] = cLowBit;

			msg = convertHexToString(Request_header, 2);

			/*
			 * 2. Replacing first 8 bytes of Request read from Test Script with
			 * 8 bytes of Message Header(4 bytes Message ID + 4 bytes Thread ID)
			 */
			/* Appending 4 bytes of Message Id to Request String */
			String tmp = "";
			int temp = msg_id;

			int len = ("" + msg_id).length();
			for (int i = 0; i < len; i++) {
				tmp = ((temp % 10) + 30) + tmp;
				temp = temp / 10;
			}

			len = tmp.length();

			for (int i = 0; i < 3 - len; i++)

				tmp = "30" + tmp;

			msg += tmp;

			/* Appending 4 bytes of Thread Id to Request String */
			temp = Thread_id;

			String str_thd_id = "";// +Thread_id;
			len = ("" + Thread_id).length();

			for (int i = 0; i < len; i++) {
				str_thd_id = ((temp % 10) + 30) + str_thd_id;
				;
				temp = temp / 10;
			}

			len = str_thd_id.length();

			/* jugaad for extra 0's coming for threadid<9 */
			if (Thread_id > 9)// &&(Thread_id<100))
				len--;

			for (int i = 0; i < 5 - len; i++) {
				str_thd_id = "30" + str_thd_id;
			}

			/* for Thread id >99 */
			if (Thread_id > 99)
				str_thd_id = "30" + str_thd_id;

			msg += str_thd_id;

			msg = convertStringToAscii(msg);

			/*
			 * Appending remaining Request Bytes to Request String
			 */
			msg += request_temp.substring(6);

			return true;
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				System.out.println("Thread: " + Thread_id
						+ " ERROR.PrepareTEPRequest [" + date.toString()
						+ "] : " + e.getMessage());// " - Disconnected : ");// +
													// Connections.hip);
			return false;
		}

	}

	/*
	 * Write Request to Output Stream used for Persistent & Transient
	 * Connections
	 */
	public void sendrequest(byte[] f) throws Exception {
		writeres: if (Connections.connstatus[Thread_id]) {
			try {
				// if(Connections.Communication)
				{
					if (convertHexToString(f, f.length) != null) {
						if (Connections.secure_channel) {
							synchronized (Connections.SSLSockets[Thread_id]) {
								/* Write Request data to Socket Output Stream */
								buffered_dataout = new DataOutputStream(
										Connections.SSLSockets[Thread_id]
												.getOutputStream());
								buffered_dataout.flush();
								buffered_dataout.write(f);

								/*
								 * Storing Request_ID & Request to a Buffer used
								 * by ReadThread for Validating Response.
								 */
								Connections.request_sent_id[Thread_id][msg_id] = request_loop;
								Connections.request_sent[Thread_id][msg_id] = convertHexToString(
										f, f.length);
								Connections.expected_response[Thread_id][msg_id] = expectedResponse;
								buffered_dataout.flush();
								buffered_dataout = null;

								if (Debug) {
									/*
									 * if(protocol.equals("PROT_RAC"))
									 * System.out.println
									 * ("WriteThread "+thread_name+
									 * "\t Connections.request_sent[Thread_id][msg_id]:"
									 * +
									 * convertStringToAscii(Connections.request_sent
									 * [
									 * Thread_id][msg_id]));//convertHexToString
									 * (f,f .length));
									 * if(protocol.equals("PROT_SOH"))
									 */
									// System.out.println("WriteThread "+Thread_id+
									// "\t "+convertHexToString(f,f.length));

								}

							}
							getresponse();
						} else {
							Connections.lock[Thread_id].lock();
							try {
								while (Connections.buffer_size[Thread_id] >= Connections.max_buffer_size) {
									Connections.notFull[Thread_id].await();
								}
							} finally {
								Connections.lock[Thread_id].unlock();
							}

							/*
							 * Connections.lock_throttle.lock(); try {
							 * while(Integer
							 * .parseInt(Connections.packetpersec)>=
							 * Connections.throttle) {
							 * //System.out.println("WriteThread : "
							 * +Thread_id+" Connections.packetpersec:"
							 * +Connections
							 * .packetpersec+" Connections.throttle"+
							 * Connections.throttle);
							 * Connections.condition_throttle.await();
							 * 
							 * } } finally { Connections.lock_throttle.unlock();
							 * }
							 */

							synchronized (Connections.Sockets[Thread_id]) {

								Connections.lock_throttle.lock();
								try {

									// synchronized (Connections.packetpersec) {
									if (Integer
											.parseInt(Connections.packetpersec) < Connections.throttle) {
										/*
										 * Write Request data to Socket Output
										 * Stream
										 */
										buffered_dataout = new DataOutputStream(
												Connections.Sockets[Thread_id]
														.getOutputStream());
										buffered_dataout.flush();
										buffered_dataout.write(f);

										/*
										 * Storing Request_ID & Request to a
										 * Buffer used by ReadThread for
										 * Validating Response.
										 */
										Connections.request_sent_id[Thread_id][msg_id] = request_loop;
										Connections.request_sent[Thread_id][msg_id] = convertHexToString(
												f, f.length);
										Connections.expected_response[Thread_id][msg_id] = expectedResponse;
										buffered_dataout.flush();
										buffered_dataout = null;

										if (Connections.buffer_size[Thread_id] < Connections.max_buffer_size)
											Connections.buffer_size[Thread_id]++;

										Integer temp = Integer
												.parseInt(Connections.packetpersec) + 1;
										Connections.packetpersec = temp
												.toString();
										// System.out.println("WriteThread : "+Thread_id+" Connections.packetpersec:"+Connections.packetpersec+" Connections.throttle"+Connections.throttle);
									}
									// }
								} finally {
									Connections.lock_throttle.unlock();
								}

							}
						}
					}
				}
			} catch (Exception e) {
				Date date = new Date();
				if (Debug)
					if (Connections.secure_channel)
						System.out
								.println("Cipher suite : "
										+ Arrays.toString(Connections.SSLSockets[Thread_id]
												.getEnabledCipherSuites()));
				System.out.println("Thread: " + Thread_id
						+ " Connections.connstatus[Thread_id]"
						+ Connections.connstatus[Thread_id] + "  ERROR.Send ["
						+ date.toString() + "] : " + e.getMessage()); // -
																		// Disconnected
																		// :
																		// ");//
																		// +
																		// Connections.hip);

				e.printStackTrace();

				if (!Connections.secure_channel) {
					if (!(mechanism.equals("Transient"))) {
						if ((Connections.Sockets[Thread_id] != null) || (Connections.Sockets[Thread_id].isClosed()==false)) 
						{
							Connections.Sockets[Thread_id].close();
							//Connections.Sockets[Thread_id] = null;
							if (Debug) {
								System.out.println("Socket closed: " + msg_id);
							}
						}
						Connections.connstatus[Thread_id] = false;

						if (Connections.initialize_Socket(Connections.hip,
								Connections.hport, Thread_id))
							Connections.connstatus[Thread_id] = true;
					}
				}
				
				if (Connections.secure_channel) 
				{
					if (request_count >= request_per_thread) 
					{
						/*
						 * Socket is closed once all responses are
						 * received for this session
						 */
						/*
						 * if(Debug)
						 * System.out.println("Thread: "+Thread_id+
						 * " Socket Closed : "
						 * +Connections.Sockets[Thread_id
						 * ]+" Request_count: " +request_count);
						 */

						if ((Connections.Sockets[Thread_id] != null) || (Connections.Sockets[Thread_id].isClosed()==false)) 
						{
							//Connections.Sockets[Thread_id].shutdownOutput();
							//Connections.Sockets[Thread_id].shutdownInput();
							Connections.SSLSockets[Thread_id].close();
							//Connections.Sockets[Thread_id] = null;
						}
						Connections.connstatus[Thread_id] = false;

						/*
						 * Connection delay between closing of
						 * socket & re-opening socket
						 */
						Thread.sleep(Connections.delay);
					}

					/*
					 * Socket initialization called for transient
					 * mechanism where Socket is closed after each
					 * session
					 */
					if (Connections.initialize_Socket(
							Connections.hip, Connections.hport,
							Thread_id)) 
					{

						Connections.connstatus[Thread_id] = true;
						Connections.start_transient_send[Thread_id] = true;
						// if(Debug)
						// System.out.println("Thread: "+Thread_id+" Socket Opened : "+Connections.Sockets[Thread_id]+" Read thread restart : "+Connections.start_transient_send[Thread_id]);
					}

				}
				// System.out.println("Receive Buffer Size : " +
				// Connections.Sockets[Thread_id].getReceiveBufferSize());

				// Thread.sleep(10000);
			}
		} else
			break writeres;
	}

	/* Write Request to Output Stream used for Connection Pooling */
	public void sendrequest_pool(byte[] f) throws Exception {
		try {
			if (convertHexToString(f, f.length) != null) {
				synchronized (socketfrompool) {
					/* Write Request data to Socket Output Stream */
					buffered_dataout = new DataOutputStream(
							socketfrompool.getOutputStream());
					buffered_dataout.flush();
					buffered_dataout.write(f);

					/*
					 * Storing Request_ID & Request to a Buffer used by
					 * ReadThread for Validating Response.
					 */
					Connections.request_sent_id[Thread_id][msg_id] = request_loop;
					Connections.request_sent[Thread_id][msg_id] = convertHexToString(
							f, f.length);

					buffered_dataout.flush();
					buffered_dataout = null;
				}

			}
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				System.out.println("Thread: " + Thread_id + " ERROR.Send ["
						+ date.toString() + "] : " + e.getMessage());// " - Disconnected : ");//
																		// +
																		// Connections.hip);
		}

	}

	/*
	 * Read Response in nttt style. Function is defined if one want to read
	 * response in the same thread.
	 */
	public boolean getresponse() throws Exception {
		String string_response = "";

		readres: {
			try {
				// System.out.println("get response");
				if (Connections.secure_channel) {
					synchronized (Connections.SSLSockets[Thread_id]) {
						// Connections.SSLSockets[Thread_id].setSoTimeout(Connections.soc_timeout);

						/*
						 * Read Response data from Socket Input Stream to
						 * Buffered Reader
						 */
						buffered_datain = new DataInputStream(
								Connections.SSLSockets[Thread_id]
										.getInputStream());

						/* Only supported for Luna EFT Host functions currently */
						if (protocol.equals("PROT_SOH")) {
							byte[] resp_header = new byte[Connections.Sfnt_Msg_Header_Length];
							int resp_len = 0;
							int header_bytes = 0, msg_bytes = 0;
							String response_temp = "";
							String request_temp = "";
							// System.out.println("Socket Read Timeout : "+Connections.SSLSockets[Thread_id].getSoTimeout());

							/*
							 * Read & Parse header (6 bytes) for reading
							 * Response length
							 */
							header_bytes = buffered_datain.read(resp_header, 0,
									resp_header.length);
							// resp_len = (short) ((resp_header[4] << 8) |
							// (resp_header[5]));
							String string_resp_header = new String(
									convertHexToString(resp_header,
											resp_header.length));
							resp_len = (int) ((Integer.parseInt(
									string_resp_header.substring(8, 10), 16) << 8) | (Integer
									.parseInt(string_resp_header.substring(10,
											12), 16)));

							if (resp_len > 0) {
								byte response[] = new byte[resp_len];

								// msg_bytes = buffered_datain.read(response, 0,
								// response.length);

								buffered_datain.readFully(response, 0,
										response.length);

								if ((new String(convertHexToString(response,
										response.length))) != null) {

									string_response = convertHexToString(
											response, response.length).trim();

									if (request_loop < request_index) {

										if (string_response.substring(0, 2)
												.equals("EE")
												|| string_response.substring(0,
														2).equals("EF") || string_response.substring(0, 2).equals("EA")) {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */
											msg_id = Integer
													.parseInt(string_response
															.substring(6, 12));// 0));

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];
											request_temp = Connections.request_sent[Thread_id][msg_id];

											Connections.request_sent_id[Thread_id][msg_id] = 0;
											Connections.request_sent[Thread_id][msg_id] = null;

											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 4 byte Function Identifier
											 */
											/*
											 * 1. Fetch Function Name and append
											 * to new Expected
											 * Response(initially ""): first 3
											 * bytes of Expected Response
											 */
											response_temp += response_list[request_loop]
													.substring(0, 6);

											/*
											 * 2. Append 2 bytes Function
											 * Count(msg_id) to Expected
											 * Response
											 */
											String tmp = "" + msg_id;

											int len = tmp.length();

											for (int i = 0; i < Connections.Sfnt_Msg_ID
													- len; i++)

												tmp = "0" + tmp;

											response_temp += tmp;

											/*
											 * 3. Append 2 bytes of Thread_id to
											 * Expected Response
											 */
											String str_thd_id = "" + Thread_id;
											len = str_thd_id.length();

											for (int i = 0; i < Connections.Sfnt_Thread_ID
													- len; i++) {
												str_thd_id = "0" + str_thd_id;
											}

											response_temp += str_thd_id;

											/*
											 * Append remaining bytes of
											 * Expected Response to New Response
											 */
											response_temp += response_list[request_loop]
													.substring(6);

										}

										else {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */

											msg_id = Integer.parseInt(
													string_response.substring(
															4, 8), 16);

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];
											request_temp = Connections.request_sent[Thread_id][msg_id];

											Connections.request_sent_id[Thread_id][msg_id] = 0;
											Connections.request_sent[Thread_id][msg_id] = null;

											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 4 byte Function Identifier
											 */
											/*
											 * 1. Fetch Function Name and append
											 * to new Expected
											 * Response(initially ""): first 3
											 * bytes of Expected Response
											 */
											response_temp += response_list[request_loop]
													.substring(0, 4);

											/*
											 * 2. Append 2 bytes Function
											 * Count(msg_id) to Expected
											 * Response
											 */
											String tmp = ""
													+ Integer
															.toHexString(msg_id);

											int len = tmp.length();

											for (int i = 0; i < 4 - len; i++)

												tmp = "0" + tmp;

											response_temp += tmp.toUpperCase();

											/*
											 * Append remaining bytes of
											 * Expected Response to New Response
											 */
											response_temp += response_list[request_loop]
													.substring(8);

										}

										if (Connections.trace_all)
											writInforTologs(request_temp,
													response_temp,
													string_response);
										else {
											/*
											 * Log only failed(BAD) test
											 * cases(Responses)
											 */
											if (!(string_response
													.startsWith(response_temp))) {
												writInforTologs(request_temp,
														response_temp,
														string_response);
											}
										}

									}
								}

								if (Debug) {
									// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
								}
							}
						}
					}
				} else {
					synchronized (Connections.Sockets[Thread_id]) {
						// Connections.Sockets[Thread_id].setSoTimeout(Connections.soc_timeout);

						/*
						 * Read Response data from Socket Input Stream to
						 * Buffered Reader
						 */
						buffered_datain = new DataInputStream(
								Connections.Sockets[Thread_id].getInputStream());

						/* Only supported for Luna EFT Host functions currently */
						if (protocol.equals("PROT_SOH")) {
							byte[] resp_header = new byte[Connections.Sfnt_Msg_Header_Length];
							short resp_len = 0;
							int header_bytes = 0, msg_bytes = 0;
							String response_temp = "";
							String request_temp = "";

							/*
							 * Read & Parse header (6 bytes) for reading
							 * Response length
							 */
							header_bytes = buffered_datain.read(resp_header, 0,
									2);

							System.out.println("After Read : ");
							resp_len = (short) ((resp_header[4] << 8) | (resp_header[5]));
							System.out.println("resp_len : " + resp_len);

							if (resp_len > 0) {
								byte response[] = new byte[resp_len];

								msg_bytes = buffered_datain.read(response, 0,
										response.length);

								if ((new String(convertHexToString(response,
										response.length))) != null) {

									string_response = convertHexToString(
											response, response.length).trim();

									System.out.println("Thread " + Thread_id
											+ " string_response : "
											+ string_response);

									if (request_loop < request_index) {
										/*
										 * Fetched 2 byte Function Count(first
										 * two bytes of Function Identifier)
										 * from Response received to be used for
										 * Response Validation
										 */

										if (string_response.substring(0, 2)
												.equals("EE")
												|| string_response.substring(0,
														2).equals("EF") || string_response.substring(0, 2).equals("EA")) {
											msg_id = Integer
													.parseInt(string_response
															.substring(6, 12));

											System.out.println(request_loop);
											System.out
													.println(request_list[request_loop]);
											System.out
													.println(response_list[request_loop]);

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];
											request_temp = Connections.request_sent[Thread_id][msg_id];
											Connections.request_sent_id[Thread_id][msg_id] = 0;
											Connections.request_sent[Thread_id][msg_id] = null;
											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 4 byte Function Identifier
											 */
											/*
											 * 1. Fetch Function Name and append
											 * to new Expected
											 * Response(initially ""): first 3
											 * bytes of Expected Response
											 */
											response_temp += response_list[request_loop]
													.substring(0, 6);

											/*
											 * 2. Append 2 bytes Function
											 * Count(msg_id) to Expected
											 * Response
											 */
											String tmp = "" + msg_id;

											int len = tmp.length();

											for (int i = 0; i < Connections.Sfnt_Msg_ID
													- len; i++)

												tmp = "0" + tmp;

											response_temp += tmp;

											/*
											 * 3. Append 2 bytes of Thread_id to
											 * Expected Response
											 */
											String str_thd_id = "" + Thread_id;
											len = str_thd_id.length();

											for (int i = 0; i < Connections.Sfnt_Thread_ID
													- len; i++) {
												str_thd_id = "0" + str_thd_id;
											}

											response_temp += str_thd_id;

											/*
											 * Append remaining bytes of
											 * Expected Response to New Response
											 */
											response_temp += response_list[request_loop]
													.substring(6);

										}

										else {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */

											msg_id = Integer.parseInt(
													string_response.substring(
															4, 8), 16);

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];
											request_temp = Connections.request_sent[Thread_id][msg_id];

											Connections.request_sent_id[Thread_id][msg_id] = 0;
											Connections.request_sent[Thread_id][msg_id] = null;

											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 4 byte Function Identifier
											 */
											/*
											 * 1. Fetch Function Name and append
											 * to new Expected
											 * Response(initially ""): first 3
											 * bytes of Expected Response
											 */
											response_temp += response_list[request_loop]
													.substring(0, 4);

											/*
											 * 2. Append 2 bytes Function
											 * Count(msg_id) to Expected
											 * Response
											 */
											String tmp = ""
													+ Integer
															.toHexString(msg_id);

											int len = tmp.length();

											for (int i = 0; i < 4 - len; i++)

												tmp = "0" + tmp;

											response_temp += tmp.toUpperCase();

											/*
											 * Append remaining bytes of
											 * Expected Response to New Response
											 */
											response_temp += response_list[request_loop]
													.substring(8);

										}

										if (Connections.trace_all) {
											System.out.println("Writing logs");
											writInforTologs(request_temp,
													response_temp,
													string_response);
										} else {
											/*
											 * Log only failed(BAD) test
											 * cases(Responses)
											 */
											if (!(string_response
													.startsWith(response_temp))) {
												writInforTologs(request_temp,
														response_temp,
														string_response);
											}
										}
									}

									if (Debug) {
										System.out.println("Thread "
												+ Thread_id + " Response : "
												+ string_response);// response_list.isFull());
									}

								}
							}
						}
					}
				}
			} catch (NullPointerException e) {
				if (Debug)
					e.printStackTrace();
				System.out.println("Thread: " + Thread_id
						+ " ReadInputstream ERROR.receive Null  : "
						+ e.toString());// /* .getMessage()*/);// +
										// " - Disconnected : " +
										// Connections.hip);
				break readres;
			} catch (ArrayIndexOutOfBoundsException e) {
				if (Debug)
					System.out
							.println(" - Buffer Empty: ReadInputstream ERROR.receive ArrayIndexoutofbound  : "
									+ e.toString());// /* .getMessage()*/);// +
													// " - Disconnected : " +
													// Connections.hip);
				break readres;
			} catch (Exception e) {
				if (Debug) {
					System.out.println("Thread: " + Thread_id + " READ ERROR ["
							+ new Date().toString() + "] : " + e.toString()
							+ "\n");// /* .getMessage()*/);// +
									// " - Disconnected : " + Connections.hip);
					e.printStackTrace();
				}
				
				if (Connections.secure_channel) 
				{
					if (request_count >= request_per_thread) 
					{
						/*
						 * Socket is closed once all responses are
						 * received for this session
						 */
						/*
						 * if(Debug)
						 * System.out.println("Thread: "+Thread_id+
						 * " Socket Closed : "
						 * +Connections.Sockets[Thread_id
						 * ]+" Request_count: " +request_count);
						 */

						if ((Connections.Sockets[Thread_id] != null) || (Connections.Sockets[Thread_id].isClosed()==false)) 
						{
							//Connections.Sockets[Thread_id].shutdownOutput();
							//Connections.Sockets[Thread_id].shutdownInput();
							Connections.SSLSockets[Thread_id].close();
							//Connections.Sockets[Thread_id] = null;
						}
						Connections.connstatus[Thread_id] = false;

						/*
						 * Connection delay between closing of
						 * socket & re-opening socket
						 */
						Thread.sleep(Connections.delay);
					}

					/*
					 * Socket initialization called for transient
					 * mechanism where Socket is closed after each
					 * session
					 */
					if (Connections.initialize_Socket(
							Connections.hip, Connections.hport,
							Thread_id)) 
					{

						Connections.connstatus[Thread_id] = true;
						Connections.start_transient_send[Thread_id] = true;
						// if(Debug)
						// System.out.println("Thread: "+Thread_id+" Socket Opened : "+Connections.Sockets[Thread_id]+" Read thread restart : "+Connections.start_transient_send[Thread_id]);
					}
					
				}
			}
		}
		return true;

	}

	/* Convert from Hex-decimal to String */
	public String convertHexToString(byte[] baInput, int iLen) {
		String strOutput = "";
		byte[] bExpandData = new byte[iLen * 2];

		for (int i = 0; i < iLen; i++) {
			bExpandData[(i * 2)] = (byte) ((baInput[i] >> 4) & (byte) 0x0F);
			if (bExpandData[(i * 2)] >= 0x00 && bExpandData[(i * 2)] <= 0x09)
				bExpandData[(i * 2)] = (byte) (bExpandData[(i * 2)] + 48);
			if (bExpandData[(i * 2)] >= 0x0a && bExpandData[(i * 2)] <= 0x0f)
				bExpandData[(i * 2)] = (byte) (bExpandData[(i * 2)] + 65 - 10);

			bExpandData[(i * 2) + 1] = (byte) (baInput[i] & (byte) 0x0F);
			if (bExpandData[(i * 2) + 1] >= 0x00
					&& bExpandData[(i * 2) + 1] <= 0x09)
				bExpandData[(i * 2) + 1] = (byte) (bExpandData[(i * 2) + 1] + 48);
			if (bExpandData[(i * 2) + 1] >= 0x0a
					&& bExpandData[(i * 2) + 1] <= 0x0f)
				bExpandData[(i * 2) + 1] = (byte) (bExpandData[(i * 2) + 1] + 65 - 10);
		}

		strOutput = new String(bExpandData);

		return strOutput;
	}

	//Corrected Mechanism to convert data from ASCII String to ASCII Hex format
	/* Convert from String to Ascii */
	public static String convertStringToAscii(final String strAsciiInput) {
		// translating text String to 7 bit ASCII encoding
		String output= new String();
        try {
			byte[] bytes = strAsciiInput.getBytes("US-ASCII");

			for (int i = 0; i < bytes.length; i++) {
				output += Integer.toHexString((int) bytes[i]);
			}

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return output;
	}


	/* Convert from String to Hex-decimal */
	public byte[] convertStringToHex(final String strInput) {
		/*
		 * There is no error checking in this function for the coming of digits
		 * other than that are allowed in hex values. 0-F
		 */
		int iInpLen = strInput.length();
		char chSingleChar;
		String tempString = "";

		if (iInpLen % 2 != 0)
			tempString = new String("0").concat(strInput);
		else
			tempString = strInput;

		iInpLen = tempString.length();

		byte[] baOutput = new byte[(iInpLen / 2)];

		for (int i = 0, j = 0; i < iInpLen; i = i + 2, j++) {
			chSingleChar = tempString.charAt(i);
			if (chSingleChar >= '0' && chSingleChar <= '9')
				baOutput[j] = (byte) (chSingleChar - 48);
			else if (chSingleChar >= 'A' && chSingleChar <= 'F')
				baOutput[j] = (byte) (chSingleChar - 65 + 10);
			else if (chSingleChar >= 'a' && chSingleChar <= 'f')
				baOutput[j] = (byte) (chSingleChar - 97 + 10);

			chSingleChar = tempString.charAt(i + 1);
			if (chSingleChar >= '0' && chSingleChar <= '9')
				baOutput[j] = (byte) ((baOutput[j] << 4) | ((byte) (chSingleChar - 48)));
			else if (chSingleChar >= 'A' && chSingleChar <= 'F')
				baOutput[j] = (byte) ((baOutput[j] << 4) | ((byte) (chSingleChar - 65 + 10)));
			else if (chSingleChar >= 'a' && chSingleChar <= 'f')
				baOutput[j] = (byte) ((baOutput[j] << 4) | ((byte) (chSingleChar - 97 + 10)));
		}

		return baOutput;
	}

	/* Read Response log for SecureChannel */
	/* Create file(if doesn't exist), open it & get file handle(Buffered Writer) */
	public void getFileHandle() {
		gethandle: try {
			String filename = "Logs\\TraceLog_ReadThread_Secchannel_"
					+ Thread_id + ".TXT";
			filelogger = new BufferedWriter(new FileWriter(filename, true));
			filelogger.flush();
		} catch (Exception ioe) {
			ioe.printStackTrace();

			if (Debug)
				System.out
						.println("Read Thread: Error while getting Handle of the file...\n"
								+ ioe);
			break gethandle;
		}
	}

	/* Close file handle */
	public void removeFileHandle() {
		if (filelogger != null) {
			try {
				filelogger.close();
			} catch (IOException localIOException1) {
			}
			filelogger = null;
		}

	}

	/* Write Trace Logs */
	public void writInforTologs(String request, String expected, String received) {
		try {
			Date date = new Date();
			filelogger.newLine();
			filelogger
					.write("**************************************************************************************************");
			filelogger.newLine();
			filelogger.write("Date: " + date.toString());
			filelogger.newLine();
			filelogger.write("Request: " + request);
			filelogger.newLine();
			filelogger.write("Expected Response: " + expected);
			filelogger.newLine();
			filelogger.write("Received Response: " + received);
			filelogger.newLine();
			filelogger
					.write("**************************************************************************************************");
			filelogger.newLine();
			filelogger.newLine();
			filelogger.flush();
		} catch (Exception ioe) {
			ioe.printStackTrace();
			if (Debug)
				System.out
						.println("ReadThread: Error when writing information to log file..\n"
								+ ioe);
		}

	}

}