/***********************************Single Thread for Exchanging Packet *************************************\\
1. Send Requests on DataOutputStream and receives response on DataInputStream on each socket as per the defined communication mechanism: Persistent, Transient or Socket_Pool
2. Message Identifier not used here.
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

public class SingleThread extends Thread {
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
	String sentRequest = "";

	SingleThread(int id, String request[], String response[], int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);

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
			
			//System.out.println("Init : Assignments");
			if (Connections.secure_channel) {
				System.out.println("Init : Checking SSL Socket Value :"+Connections.SSLSockets[Thread_id]);
				synchronized (Connections.SSLSockets[Thread_id]) {
					//System.out.println("Init : Opening DataoutputStream");
					buffered_dataout = new DataOutputStream(Connections.SSLSockets[Thread_id].getOutputStream());
				}
			} else {
				synchronized (Connections.Sockets[Thread_id]) {
					buffered_dataout = new DataOutputStream(Connections.Sockets[Thread_id].getOutputStream());
				}
			}
			getFileHandle();
		} catch (Exception e) {
			if (Debug)
				System.out.println("WriteThread ERROR.init : " + e.getMessage());
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
				while (true) {
					if (request_loop < request_index) {
						/*
						 * Request String prepared for Luna EFT Host functions
						 */
						if (protocol.equals("PROT_SOH")) {

							if (prepareLunaEFTRequest()) {
								//System.out.println("Prepare Request");
								byte[] f = convertStringToHex(msg.trim());
								
								sendrequest(f);
								//System.out.println("Send Request");
								getresponse();
								//System.out.println("Received Response");
							}

						} else if (protocol.equals("PROT_RAC")) {
							if (prepareTEPRequest()) {
								/* Converting Request String to ASCII format */
								//byte[] f = msg.getBytes("US-ASCII");
								byte[] f = convertStringToHex(msg.trim());

								/* Send Request to OutputStream */
								sendrequest(f);
								getresponse();
							}
						}
					}

					request_loop++;

					if (request_loop >= request_index) {
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
				while (true) {
					request_loop = 0;
					request_count = 0;

					while (request_count < request_per_thread) {
						if (request_loop < request_index) {
							/*
							 * Request String prepared for Luna EFT Host
							 * functions
							 */
							if (protocol.equals("PROT_SOH")) {
								if (prepareLunaEFTRequest()) {
									byte[] f = convertStringToHex(msg.trim());

									//System.out.println("Thread ID: " + Thread_id + "\tSent Packet : " + request_count);
									sendrequest(f);
									getresponse();
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
									/* Converting Request String to ASCII format */
									//byte[] f = msg.getBytes("US-ASCII");
									byte[] f = convertStringToHex(msg.trim());

									/* Send Request to OutputStream */
									sendrequest(f);
									getresponse();
								}
							}

							request_loop++;
							request_count++;
						}

						if (request_loop >= request_index) {
							request_loop = 0;
						}
					}

					/*
					 * Closing and re-initializing Socket
					 */
					if (Connections.secure_channel)
						Connections.SSLSockets[Thread_id].close();
					else
						Connections.Sockets[Thread_id].close();

					Connections.connstatus[Thread_id] = false;

					/*
					 * Connection delay between closing of socket & re-opening
					 * socket
					 */
					Thread.sleep(Connections.delay);

					/*
					 * Socket initialization called for transient mechanism
					 * where Socket is closed after each session
					 */
					if (Connections.initialize_Socket(Connections.hip, Connections.hport, Thread_id))
						Connections.connstatus[Thread_id] = true;

					continue;
				}
			} catch (Exception e) {
				if (Debug) {
					System.out.println("ERROR.send.run : " + request_loop);
					e.printStackTrace();// .getMessage());
				}
			} finally {
				break;
			}

			/* case "Socket_Pool": /* Connection Pooling */

			/*
			 * try { while (request_count < request_per_thread) { if
			 * (request_loop < request_index) { /* Request String prepared for
			 * Luna EFT Host functions
			 */
			/*
			 * if (protocol.equals("PROT_SOH")) { if (prepareLunaEFTRequest()) {
			 * byte[] f = convertStringToHex(msg.trim()); sendrequest_pool(f);
			 * 
			 * msg_id++;
			 * 
			 * /* Message id ranges from 0 to 9999
			 */
			/*
			 * if (msg_id > (Connections.max_buffer_size - 1)) msg_id = 0; } }
			 * else if (protocol.equals("PROT_RAC"))// && //
			 * (!Connections.thalespacketsent))/*Request // String // prepared
			 * for // Thales Host // functions
			 */
			/*
			 * { if (prepareTEPRequest()) { /* Converting Request String to
			 * ASCII format
			 */
			/*
			 * byte[] f = msg.getBytes("US-ASCII");
			 * 
			 * /* Send Request to OutputStream
			 */
			/*
			 * sendrequest_pool(f);
			 * 
			 * msg_id++;
			 * 
			 * /* Message Identifier varies from 0 to 99
			 */
			/*
			 * if (msg_id > 99) msg_id = 0; }
			 * 
			 * } } request_loop++; request_count++;
			 * 
			 * if (request_loop >= request_index) { request_loop = 0; }
			 * 
			 * continue; } } catch (Exception e) { if (Debug) {
			 * System.out.println( "ERROR.send.run : " + request_loop);
			 * e.printStackTrace(); } } finally { break; }
			 */
		}
	}

	/*
	 * Prepares LunaEFT Request Message = Safenet Request Header + Request
	 * Message
	 */
	public boolean prepareLunaEFTRequest() throws Exception {
		String request_temp = "";
		String response_temp = "";
		expectedResponse = "";

		try {
			// System.out.println("Response : "+response_list[request_loop]);
			request_temp = request_list[request_loop];
			response_temp = response_list[request_loop];
			this.sentRequest = request_list[request_loop];
			this.expectedResponse = response_list[request_loop];

			int itemp;
			int requestlength = (request_temp.length() / 2);

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

			msg = convertHexToString(Request_header, Connections.Sfnt_Msg_Header_Length);

			/*
			 * 2. Appending 3 bytes of Function name to Request String
			 */
			msg += request_temp;
			//expectedResponse += response_temp;

			// System.out.println("Send message: "+msg);

			return true;
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				e.printStackTrace();
			System.out.println("Thread: " + Thread_id + " ERROR.PrepareLunaEFTRequest [" + date.toString() + "] : "
					+ e.getMessage());// " - Disconnected : ");// +//
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
			// request_temp = request_list[request_loop];
			request_temp = parseTEPString(request_list[request_loop]);
			//System.out.println(request_list[request_loop]);
			
			this.sentRequest = request_list[request_loop];
			this.expectedResponse = response_list[request_loop];
			
			int itemp;
			int requestlength = request_temp.length() / 2;

			/* 1. Preparing 2 byte Request Header */
			byte[] Request_header = new byte[2];

			itemp = (requestlength) & 0x0000FFFF;
			byte cLowBit = (byte) itemp;

			itemp = (requestlength / 256); // & 0xFFFF0000;
			byte cHighBit = (byte) itemp;

			Request_header[0] = cHighBit;
			Request_header[1] = cLowBit;

			msg = convertHexToString(Request_header, 2);

			/*
			 * Appending remaining Request Bytes to Request String
			 */
			msg += request_temp;
			//System.out.println(msg);

			return true;
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				System.out.println("Thread: " + Thread_id + " ERROR.PrepareTEPRequest [" + date.toString() + "] : "
						+ e.getMessage());// " - Disconnected : ");// +
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

				if (convertHexToString(f, f.length) != null) {
					if (Connections.secure_channel) {						

						//if (Integer.parseInt(Connections.packetpersec) < Connections.throttle) {
							
							synchronized (Connections.SSLSockets[Thread_id]) {

							/* Write Request data to Socket Output Stream */
							//System.out.println("Before sending");
							buffered_dataout = new DataOutputStream(Connections.SSLSockets[Thread_id].getOutputStream());
							//System.out.println("Data Output Stream created sending");
							buffered_dataout.flush();
							buffered_dataout.write(f);
							//System.out.println("Data Written");

							//sentRequest = convertHexToString(f, f.length);

							buffered_dataout.flush();
							buffered_dataout = null;
							Integer temp = Integer.parseInt(Connections.packetpersec) + 1;
							Connections.packetpersec = temp.toString();
							}
						//}
					} else {

						// synchronized (Connections.packetpersec) {
						//if (Integer.parseInt(Connections.packetpersec) < Connections.throttle) {
							synchronized (Connections.Sockets[Thread_id]) {
							/*
							 * Write Request data to Socket Output Stream
							 */
							buffered_dataout = new DataOutputStream(Connections.Sockets[Thread_id].getOutputStream());
							buffered_dataout.flush();
							buffered_dataout.write(f);
								
							//sentRequest = convertHexToString(f, f.length);

							buffered_dataout.flush();
							buffered_dataout = null;

							Integer temp = Integer.parseInt(Connections.packetpersec) + 1;
							Connections.packetpersec = temp.toString();
							}
						//}
					}
				}

			} catch (Exception e) {
				Date date = new Date();
				/*if (Debug)
					if (Connections.secure_channel)*/
					//	System.out.println("Cipher suite : "+ Arrays.toString(Connections.SSLSockets[Thread_id].getEnabledCipherSuites()));
				/*System.out.println("Thread: " + Thread_id + " Connections.connstatus[Thread_id]"
						+ Connections.connstatus[Thread_id] + "  ERROR.Send [" + date.toString() + "] : "
						+ e.getMessage()); */// -
											// Disconnected
											// :
											// ");//
											// +
											// Connections.hip);

				//e.printStackTrace();

				/*if (!Connections.secure_channel) {
					if (!(mechanism.equals("Transient"))) {
						if ((Connections.Sockets[Thread_id] != null)
								|| (Connections.Sockets[Thread_id].isClosed() == false)) {
							// Connections.Sockets[Thread_id].shutdownOutput();
							// Connections.Sockets[Thread_id].shutdownInput();
							Connections.Sockets[Thread_id].close();
							// Connections.Sockets[Thread_id] = null;
							if (Debug) {
								System.out.println("Socket closed: " + this.Thread_id);
							}
						}
						Connections.connstatus[Thread_id] = false;

						if (Connections.initialize_Socket(Connections.hip, Connections.hport, Thread_id))
							Connections.connstatus[Thread_id] = true;
					}
				}*/

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
					buffered_dataout = new DataOutputStream(socketfrompool.getOutputStream());
					buffered_dataout.flush();
					buffered_dataout.write(f);

					/*
					 * Storing Request_ID & Request to a Buffer used by
					 * ReadThread for Validating Response.
					 */
					// Connections.request_sent_id[Thread_id][msg_id] =
					// request_loop;
					// Connections.request_sent[Thread_id][msg_id] =
					// convertHexToString(f, f.length);

					buffered_dataout.flush();
					buffered_dataout = null;
				}

			}
		} catch (Exception e) {
			Date date = new Date();
			if (Debug)
				System.out
						.println("Thread: " + Thread_id + " ERROR.Send [" + date.toString() + "] : " + e.getMessage());// "
																														// -
																														// Disconnected
																														// :
																														// ");//
																														// +
																														// Connections.hip);
		}

	}

	/*
	 * Read Response in same thread. Function is defined if one want to read
	 * response in the same thread.
	 */
	public boolean getresponse() throws Exception {		
		readres: 
		{
			String received_response = "";
			
			byte[] resp_header = new byte[Connections.Sfnt_Msg_Header_Length];
			int resp_len = 0;
			int header_bytes = 0, msg_bytes = 0;
			try 
			{
				/*
				 * Read Response data from Socket Input Stream to Buffered
				 * Reader
				 */
				if (Connections.secure_channel) {
					synchronized (Connections.SSLSockets[Thread_id]) {
						buffered_datain = new DataInputStream(Connections.SSLSockets[Thread_id].getInputStream());
						
						if (protocol.equals("PROT_SOH")) 
						{
						/*
						 * Parsing Response Header. Reading Response bytes. Comparing
						 * with Expected Response Logging to Trace file.
						 */
						header_bytes = buffered_datain.read(resp_header, 0, resp_header.length);

						String string_resp_header = new String(convertHexToString(resp_header, resp_header.length));
						resp_len = (int) ((Integer.parseInt(string_resp_header.substring(8, 10), 16) << 8)
								| (Integer.parseInt(string_resp_header.substring(10, 12), 16)));
						

						if (resp_len > 0) {
							byte response[] = new byte[resp_len];

							buffered_datain.readFully(response, 0, response.length);

							if ((new String(convertHexToString(response, response.length))) != null) {

								received_response = new String(convertHexToString(response, response.length).trim());
								

								if (Connections.trace_all)
									writInforTologs(this.sentRequest, this.expectedResponse, received_response);
								else {
									/*
									 * Log only failed(BAD) test cases(Responses)
									 */
									if (!(received_response.startsWith(this.expectedResponse)))
										writInforTologs(this.sentRequest, this.expectedResponse, received_response);
								}

							}
						}
					}					
					else if (protocol.equals("PROT_RAC"))
					{
						
						resp_header = new byte[2];
						short resp_len_1 = 0;
						
						/*
						 * Read & Parse header (2 bytes) for reading
						 * Response length
						 */
						buffered_datain = new DataInputStream(
								Connections.Sockets[Thread_id]
										.getInputStream());
						int bytes_read = buffered_datain.read(resp_header, 0, resp_header.length);
					
						resp_len_1 = (short) ((resp_header[0] << 8) | (resp_header[1]));
						//System.out.println("Reading Response Data ");
										
						if (resp_len_1 > 0) 
						{
							/*
							 * Read Response data from Socket Input Stream to
							 * Buffered Reader
							 */
							byte response[] = new byte[resp_len_1];
							msg_bytes = buffered_datain.read(response,0, resp_len_1);//response.length);
							//System.out.println("Response Data Read Success");
		
							if ((new String(response)) != null) 
							{
								received_response = new String(response);
							}
		
							//System.out.println(received_response);
							if (Connections.trace_all)
								writInforTologs(this.sentRequest, this.expectedResponse, received_response);
							else {
								/*
								 * Log only failed(BAD) test cases(Responses)
								 */
								if (!(received_response.startsWith(this.expectedResponse)))
									writInforTologs(this.sentRequest, this.expectedResponse, received_response);
					}
				}
					}
					}
				} else {
					synchronized (Connections.Sockets[Thread_id]) {

						/*
						 * Read Response data from Socket Input Stream to
						 * Buffered Reader
						 */
						buffered_datain = new DataInputStream(Connections.Sockets[Thread_id].getInputStream());
						//System.out.println("Before Header Read");
						if (protocol.equals("PROT_SOH")) 
						{
							/*
							 * Parsing Response Header. Reading Response bytes. Comparing
							 * with Expected Response Logging to Trace file.
							 */
							header_bytes = buffered_datain.read(resp_header, 0, resp_header.length);
							
							//System.out.println("Header Read");
							String string_resp_header = new String(convertHexToString(resp_header, resp_header.length));
							resp_len = (int) ((Integer.parseInt(string_resp_header.substring(8, 10), 16) << 8)
									| (Integer.parseInt(string_resp_header.substring(10, 12), 16)));
							
							//System.out.println(string_resp_header);
							if (resp_len > 0) {
								byte response[] = new byte[resp_len];
	
								buffered_datain.readFully(response, 0, response.length);
	
								if ((new String(convertHexToString(response, response.length))) != null) {
	
									received_response = new String(convertHexToString(response, response.length).trim());
									
									//System.out.println(received_response);
									if (Connections.trace_all)
										writInforTologs(this.sentRequest, this.expectedResponse, received_response);
									else {
										/*
										 * Log only failed(BAD) test cases(Responses)
										 */
										if (!(received_response.startsWith(this.expectedResponse)))
											writInforTologs(this.sentRequest, this.expectedResponse, received_response);
									}
	
								}
							}
						}
						else if (protocol.equals("PROT_RAC"))
						{
							
							resp_header = new byte[2];
							short resp_len_1 = 0;
							
							/*
							 * Read & Parse header (2 bytes) for reading
							 * Response length
							 */
							buffered_datain = new DataInputStream(
									Connections.Sockets[Thread_id]
											.getInputStream());
							int bytes_read = buffered_datain.read(resp_header, 0, resp_header.length);
						
							resp_len_1 = (short) ((resp_header[0] << 8) | (resp_header[1]));
							//System.out.println("Reading Response Data ");
											
							if (resp_len_1 > 0) 
							{
								/*
								 * Read Response data from Socket Input Stream to
								 * Buffered Reader
								 */
								byte response[] = new byte[resp_len_1];
								msg_bytes = buffered_datain.read(response,0, resp_len_1);//response.length);
								//System.out.println("Response Data Read Success");
			
								if ((new String(response)) != null) 
								{
									received_response = new String(response);
								}
			
								//System.out.println(received_response);
								if (Connections.trace_all)
									writInforTologs(this.sentRequest, this.expectedResponse, received_response);
								else {
									/*
									 * Log only failed(BAD) test cases(Responses)
									 */
									if (!(received_response.startsWith(this.expectedResponse)))
										writInforTologs(this.sentRequest, this.expectedResponse, received_response);
						}
					}
				}
			}
			}

			} catch (

			NullPointerException e) {
				/*if (Debug)
					e.printStackTrace();
				System.out.println("Thread: " + Thread_id + " ReadInputstream ERROR.receive Null  : " + e.toString());*/
				break readres;
			} catch (ArrayIndexOutOfBoundsException e) {
				if (Debug)
					System.out.println(
							" - Buffer Empty: ReadInputstream ERROR.receive ArrayIndexoutofbound  : " + e.toString());										
				break readres;
			} catch (Exception e) 
			{
				if (Debug) {
					System.out.println("Thread: " + Thread_id + " READ ERROR [" + new Date().toString() + "] : "
							+ e.toString() + "\n");// /* .getMessage()*/);// +
													// " - Disconnected : " +
													// Connections.hip);
					e.printStackTrace();
				}

				
				/*if (Connections.secure_channel) 
				{

					if ((Connections.SSLSockets[Thread_id] != null)
								|| (Connections.SSLSockets[Thread_id].isClosed() == false)) 
					{
	
							Connections.SSLSockets[Thread_id].close();
					}
					Connections.connstatus[Thread_id] = false;
				}
				else
				{
					if ((Connections.Sockets[Thread_id] != null)
						|| (Connections.Sockets[Thread_id].isClosed() == false)) {

						Connections.Sockets[Thread_id].close();
					}
					Connections.connstatus[Thread_id] = false;
				}

					/*
					 * Connection delay between closing of socket & re-opening
					 * socket
					 */
				/*Thread.sleep(Connections.delay);
				

				/*
				 * Socket initialization called for transient mechanism where
				 * Socket is closed after each session
				 */
				/*if (Connections.initialize_Socket(Connections.hip, Connections.hport, Thread_id)) {

					Connections.connstatus[Thread_id] = true;
				}*/

			}

		}
		return true;

	}

	public static String parseTEPString(String Data) {
		String request_output = "";
		String request_temp = "";
		int converted_index = 0;

		request_output = Data;
		converted_index = 0;

		while (request_output.substring(converted_index, request_output.length()).indexOf("x") > -1) {
			request_temp = convertStringToAscii(request_output.substring(converted_index, request_output.indexOf("x")));
			request_temp += request_output.substring(request_output.indexOf("x") + 1, request_output.indexOf("x") + 3);
			converted_index = request_output.indexOf("x") + 3;
		}
		request_temp += convertStringToAscii(request_output.substring(converted_index, request_output.length()));

		return request_temp;
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

	/* Convert from String to Ascii */
	public static String convertStringToAscii(final String strAsciiInput) {
		// translating text String to 7 bit ASCII encoding
		String output = new String();
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

	/* Convert from String to Ascii */
	public static String convertBytesToAscii(byte[] bytes) {
		// translating text String to 7 bit ASCII encoding
		String output = new String();

		// translating text String to 7 bit ASCII encoding
		for (int i = 0; i < bytes.length; i++) {
			output += Integer.toHexString((int) bytes[i]);
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
	/*
	 * Create file(if doesn't exist), open it & get file handle(Buffered Writer)
	 */
	public void getFileHandle() {
		gethandle: try {
			String filename = "Logs\\TraceLog_" + Thread_id + ".TXT";
			filelogger = new BufferedWriter(new FileWriter(filename, true));
			filelogger.flush();
		} catch (Exception ioe) {
			ioe.printStackTrace();

			if (Debug)
				System.out.println("Read Thread: Error while getting Handle of the file...\n" + ioe);
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
			filelogger.write(
					"**************************************************************************************************");
			filelogger.newLine();
			filelogger.write("Date: " + date.toString());
			filelogger.newLine();
			filelogger.write("Request: " + request);
			filelogger.newLine();
			filelogger.write("Expected Response: " + expected);
			filelogger.newLine();
			filelogger.write("Received Response: " + received);
			filelogger.newLine();
			filelogger.write(
					"**************************************************************************************************");
			filelogger.newLine();
			filelogger.newLine();
			filelogger.flush();
		} catch (Exception ioe) {
			ioe.printStackTrace();
			if (Debug)
				System.out.println("ReadThread: Error when writing information to log file..\n" + ioe);
		}

	}

}