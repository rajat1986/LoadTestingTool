/***********************************Read Response Thread *************************************\\
1. Read Responses on DataInputStream on each socket as per the defined communication mechanism: Persistent, Transient or Socket_Pool
2. Validate the Received Response with the Expected Response corresponding to the Request Sent.
3. Logs the traces to TraceLog file created for each thread. Log Type : BAD for failed messages , ALL for all messages
\\******************************************************************************************/
package Stress;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;

import edu.emory.mathcs.util.net.Connection;

public class ReadThread extends Thread {
	private int msg_id = 0;
	private int amb_msg_id = 0;
	private int Thread_id = 0;
	private Thread rt;
	private String thread_name;
	private byte[] response;
	private DataInputStream buffered_datain;
	private int ResponseBufferIndex = 0;
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
	private Connection conn;
	private String protocol = "";
	boolean message_read_status = false;

	ReadThread(int id, String request[], String response[], int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);

			synchronized (Connections.Sockets[Thread_id]) {
				request_list = new String[request_index];
				response_list = new String[request_index];
				this.request_index = request_index;
				Debug = Connections.Debug;
				mechanism = Connections.mechanism;
				protocol = Connections.protocol;

				if (mechanism.equals("Transient")) {
					request_per_thread = Connections.request_per_thread;
				}

				for (int i = 0; i < request_index; i++) {
					request_list[i] = new String(request[i]);
					response_list[i] = new String(response[i]);
				}
				// Connections.Sockets[Thread_id].setSoTimeout(Connections.soc_timeout);
				buffered_datain = new DataInputStream(
						Connections.Sockets[Thread_id].getInputStream());
			}
			getFileHandle();
		} catch (Exception e) {
			if (Debug)
				System.out.println("ReadThread ERROR.init : " + e.getMessage());
		}
		rt = new Thread(this, thread_name);
		rt.start(); // Start the thread
	}

	ReadThread(int id, Socket socket, Connection conn, String request[],
			String response[], int request_index) {
		try {
			Thread_id = id;
			thread_name = Integer.toString(Thread_id);
			this.conn = conn;
			socketfrompool = socket;

			synchronized (socketfrompool) {
				request_list = new String[request_index];
				response_list = new String[request_index];
				this.request_index = request_index;
				Debug = Connections.Debug;
				mechanism = Connections.mechanism;
				protocol = Connections.protocol;

				request_per_thread = Connections.request_per_thread;

				for (int i = 0; i < request_index; i++) {
					request_list[i] = new String(request[i]);
					response_list[i] = new String(response[i]);
				}

				// socket.setSoTimeout(Connections.soc_timeout);
				buffered_datain = new DataInputStream(socket.getInputStream());
			}
			getFileHandle();
		} catch (Exception e) {
			if (Debug)
				System.out.println("ReadThread ERROR.init : " + e.getMessage());
		}
		rt = new Thread(this, thread_name);
		rt.start(); // Start the thread
	}

	/*
	 * Different Stress Mechanisms implemented for Reading Response from
	 * InputStream
	 */
	public void run() {
		switch (mechanism) {
		case "Persistent": /* Persistent Connections */

			try {
				while (true)
					getresponse();
			} catch (Exception e) {
				try {
					e.printStackTrace();
					if (Debug)
						System.out.println("ERROR.receive.run : "
								+ e.getMessage());
				} catch (Exception localInterruptedException) {

				}
			} finally {
				break;
			}

		case "Transient": /* Transient Connections */
			try {
				while (true) {
					msg_id = 0;
					request_loop = 0;
					request_count = 0;
					int GetResponse_failed_attempt = 0;

					while (request_count < request_per_thread) {
						while (getresponse() == false)
							GetResponse_failed_attempt++;

						request_count++;
					}

					if (request_count >= request_per_thread) {
						/*
						 * Socket is closed once all responses are received for
						 * this session
						 */
						/*
						 * if(Debug) System.out.println("Thread: "+Thread_id+
						 * " Socket Closed : "
						 * +Connections.Sockets[Thread_id]+" Request_count: "
						 * +request_count);
						 */

						if ((Connections.Sockets[Thread_id] != null) || (Connections.Sockets[Thread_id].isClosed()==false)) 
						{
							Connections.Sockets[Thread_id].close();
							//Connections.Sockets[Thread_id] = null;
						}
						Connections.connstatus[Thread_id] = false;

						/*
						 * Connection delay between closing of socket &
						 * re-opening socket
						 */
						Thread.sleep(Connections.delay);

						/*
						 * Socket initialization called for transient mechanism
						 * where Socket is closed after each session
						 */
						if (Connections.initialize_Socket(Connections.hip,
								Connections.hport, Thread_id)) {

							Connections.connstatus[Thread_id] = true;
							Connections.start_transient_send[Thread_id] = true;
							// if(Debug)
							// System.out.println("Thread: "+Thread_id+" Socket Opened : "+Connections.Sockets[Thread_id]+" Read thread restart : "+Connections.start_transient_send[Thread_id]);
						}

						Connections.ThreadCompleted[Thread_id] = true;
					}

				}

			} catch (Exception e) {
				try {
					if (Debug)
						System.out.println("ERROR.receive.run : "
								+ e.getMessage());
				} catch (Exception localInterruptedException) {

				}
			} finally {
				break;
			}

		case "Socket_Pool": /* Connection Pooling */
			try {

				while (request_count < request_per_thread) {
					if (getresponse_pool() == false)
						break;
				}

				/*
				 * Socket connection returned to Socket Pool to be further
				 * reused by other threads
				 */
				conn.returnToPool();

			} catch (Exception e) {
				try {
					if (Debug)
						System.out.println("ERROR.receive.run : "
								+ e.getMessage());
				} catch (Exception localInterruptedException) {

				}
			} finally {
				break;
			}
		}

	}

	/*
	 * Read Response from Input Stream & Validating Response used for Persistent
	 * & Transient Connections
	 */
	public boolean getresponse() throws Exception {
		int timeout = 100;
		int data_received;
		String string_response = "";
		message_read_status = false;

		data_received = 0;

		if (Connections.connstatus[Thread_id]) {
			try {
				/* Response Handling, If Secure Channel is enabled */
				if (Connections.secure_channel) {
					synchronized (Connections.SSLSockets[Thread_id]) {

						if (Connections.SSLSockets[Thread_id].isConnected()) {
							/*
							 * Read Response data from Socket Input Stream to
							 * Buffered Reader
							 */
							buffered_datain = new DataInputStream(
									Connections.SSLSockets[Thread_id]
											.getInputStream());
							data_received = buffered_datain.available();

							/*
							 * Only supported for Luna EFT Host functions
							 * currently
							 */
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
								header_bytes = buffered_datain.read(
										resp_header, 0, resp_header.length);
								resp_len = (short) ((resp_header[4] << 8) | (resp_header[5]));

								if (resp_len > 0) {
									response = new byte[resp_len];

									msg_bytes = buffered_datain.read(response,
											0, response.length);

									if ((new String(convertHexToString(
											response, response.length))) != null) {
										string_response = convertHexToString(
												response, response.length)
												.trim();

										if (request_loop < request_index) {
											if (string_response.substring(0, 2)
													.equals("EE")
													|| string_response
															.substring(0, 2)
															.equals("EF") || string_response.substring(0, 2).equals("EA")) {
												/*
												 * Fetched 2 byte Function
												 * Count(first two bytes of
												 * Function Identifier) from
												 * Response received to be used
												 * for Response Validation
												 */
												msg_id = Integer
														.parseInt(string_response
																.substring(6,
																		12));// 0));

												/*
												 * Use Function Identifier to
												 * read Request & Response from
												 * buffers using Function
												 * Count(msg_id)
												 */
												request_loop = Connections.request_sent_id[Thread_id][msg_id];
												request_temp = Connections.request_sent[Thread_id][msg_id];

												Connections.request_sent_id[Thread_id][msg_id] = 0;
												Connections.request_sent[Thread_id][msg_id] = null;

												/*
												 * Prepare Expected Response for
												 * Response Validation that
												 * contains 4 byte Function
												 * Identifier
												 */
												/*
												 * 1. Fetch Function Name and
												 * append to new Expected
												 * Response(initially ""): first
												 * 3 bytes of Expected Response
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
												 * 3. Append 2 bytes of
												 * Thread_id to Expected
												 * Response
												 */
												String str_thd_id = ""
														+ Thread_id;
												len = str_thd_id.length();

												for (int i = 0; i < Connections.Sfnt_Thread_ID
														- len; i++) {
													str_thd_id = "0"
															+ str_thd_id;
												}

												response_temp += str_thd_id;

												/*
												 * Append remaining bytes of
												 * Expected Response to New
												 * Response
												 */
												response_temp += response_list[request_loop]
														.substring(6);

												/*
												 * For Luna EFT functions,
												 * Function Count ranges from 0
												 * to 99999
												 */
												if (msg_id > (Connections.max_buffer_size - 1))// 99999
													msg_id = 0;
											} else {
												/*
												 * Fetched 2 byte Function
												 * Count(first two bytes of
												 * Function Identifier) from
												 * Response received to be used
												 * for Response Validation
												 */

												// amb_msg_id =
												// Integer.parseInt(string_response.substring(4,
												// 8));
												msg_id++;
												/*
												 * For Luna EFT functions,
												 * Function Count ranges from 0
												 * to 99999
												 */
												if (msg_id > (Connections.max_buffer_size - 1))
													msg_id = 0;

												/*
												 * Use Function Identifier to
												 * read Request & Response from
												 * buffers using Function
												 * Count(msg_id)
												 */
												request_loop = Connections.request_sent_id[Thread_id][msg_id];
												request_temp = Connections.request_sent[Thread_id][msg_id];

												Connections.request_sent_id[Thread_id][msg_id] = 0;
												Connections.request_sent[Thread_id][msg_id] = null;

												/*
												 * Prepare Expected Response for
												 * Response Validation that
												 * contains 4 byte Function
												 * Identifier
												 */
												/*
												 * 1. Fetch Function Name and
												 * append to new Expected
												 * Response(initially ""): first
												 * 3 bytes of Expected Response
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

												response_temp += tmp;

												/*
												 * 3. Append 2 bytes of
												 * Thread_id to Expected
												 * Response
												 */

												/*
												 * RJK String str_thd_id = "" +
												 * Thread_id; int len =
												 * str_thd_id.length();
												 * 
												 * for (int i = 0; i < 4 - len;
												 * i++) { str_thd_id = "0" +
												 * str_thd_id; }
												 * 
												 * response_temp += str_thd_id;
												 */

												/*
												 * Append remaining bytes of
												 * Expected Response to New
												 * Response
												 */
												response_temp += response_list[request_loop]
														.substring(8);

											}

											/*
											 * if (Connections.trace_all)
											 * writInforTologs(request_temp,
											 * response_temp, string_response);
											 * else { /* Log only failed(BAD)
											 * test cases(Responses)
											 */
											/*
											 * if (!(string_response
											 * .startsWith(response_temp))) {
											 * writInforTologs( request_temp,
											 * response_temp, string_response);
											 * } }
											 */

											request_loop++;

											if (request_loop >= request_index) {
												request_loop = 0;
											}

											if (Debug) {
												// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
											}
											// request_count++;

											return true;
										}
									}
								}
							}
						}
					}

				} else {
					/*
					 * Response Handling when Secure Channel is disabled -
					 * Applicable to both Luna EFT Host Functions & Thales Host
					 * Functions
					 */

					/*
					 * synchronized (Connections.Sockets[Thread_id]) {
					 */
					if (Connections.Sockets[Thread_id].isConnected()) {
						/*
						 * Read Response data from Socket Input Stream to
						 * Buffered Reader
						 */
						synchronized (Connections.Sockets[Thread_id]) {
							buffered_datain = new DataInputStream(
									Connections.Sockets[Thread_id]
											.getInputStream());
							data_received = buffered_datain.available();
						}

						if (data_received > 0) {
							timeout = 100;
							/*
							 * Response Handling for Luna EFT Host Functions
							 * with Secure Channel Disabled
							 */
							if (protocol.equals("PROT_SOH")) {
								byte[] resp_header = new byte[Connections.Sfnt_Msg_Header_Length];
								int resp_len = 0;
								int header_bytes = 0, msg_bytes = 0;
								String response_temp = "";
								String request_temp = "";
								Date date = new Date();

								if (Connections.Debug) {
									// System.out.println(date.toString()+" Send Buffer size : "+Connections.Sockets[Thread_id].getSendBufferSize());
									// System.out.println(date.toString()+" Receive Buffer size : "+Connections.Sockets[Thread_id].getReceiveBufferSize());
								}

								/*
								 * Read & Parse header (6 bytes) for reading
								 * Response length
								 */
								synchronized (Connections.Sockets[Thread_id]) 
								{
									//Corrected mechanism to read all the header bytes.
									
									/*header_bytes = buffered_datain.read(
											resp_header, 0, resp_header.length);*/
									buffered_datain.readFully(resp_header,0,resp_header.length);
									
								}
								
								//Not needed but still kept for debugging purpose
								
								/*if (header_bytes != resp_header.length) {
									if (Connections.Debug)
										System.out
												.println(date.toString()
														+ "  1Response bytes received: "
														+ header_bytes);
								}*/


								String string_resp_header = new String(
										convertHexToString(resp_header,
												resp_header.length));

								resp_len = (int) ((Integer
										.parseInt(string_resp_header.substring(
												8, 10), 16) << 8) | (Integer
										.parseInt(string_resp_header.substring(
												10, 12), 16)));

								if (resp_len > 0) {
									response = new byte[resp_len];

									synchronized (Connections.Sockets[Thread_id]) {
										buffered_datain.readFully(response, 0,
												response.length);
									}

									/*
									 * while(recvd_bytes!=resp_len) { response =
									 * new byte[bytes_to_read]; msg_bytes =
									 * buffered_datain.read(response,
									 * read_offset ,bytes_to_read);
									 * //buffered_datain.readFully(response,
									 * 0,response.length); if ((new
									 * String(convertHexToString(response,
									 * msg_bytes))) != null) { string_response
									 * += convertHexToString(response,
									 * msg_bytes).trim(); recvd_bytes +=
									 * msg_bytes; read_offset = recvd_bytes;
									 * bytes_to_read = bytes_to_read -
									 * msg_bytes; } }
									 */

									if ((new String(convertHexToString(
											response, response.length))) != null) {
										string_response = convertHexToString(
												response, response.length)
												.trim();

										/*
										 * if (request_loop < request_index) {
										 */// Rajat Commented 20.07.2016
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

											/*
											 * if(Connections.lock[Thread_id].
											 * tryLock()) {
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];

											request_temp = Connections.request_sent[Thread_id][msg_id];

											Connections.request_sent_id[Thread_id][msg_id] = 0;
											response_temp = Connections.expected_response[Thread_id][msg_id];

											Connections.request_sent[Thread_id][msg_id] = null;
											Connections.expected_response[Thread_id][msg_id] = null;

											Connections.lock[Thread_id].lock();

											try {
												Connections.buffer_size[Thread_id]--;
												if ((Connections.buffer_size[Thread_id] + 1) >= Connections.max_buffer_size) {
													Connections.notFull[Thread_id]
															.signalAll();
													/*
													 * if (Debug)
													 * System.out.println
													 * ("ReadThread "+Thread_id+
													 * "\t "
													 * +Connections.buffer_size
													 * [Thread_id]
													 * +"\tLock Freed");
													 */
												}

											} finally {
												Connections.lock[Thread_id]
														.unlock();
											}

										} else {
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
											if (Connections.Debug) {
												// System.out.println("ReadThread "+Thread_id+" ThreadID : "+Thread_id+"  Msg_ID : "+msg_id);//response_list.isFull());
												// System.out.println("Read Thread "+Thread_id+" Request : "+Connections.request_sent[Thread_id][msg_id]);//response_list.isFull());
											}
											Connections.request_sent_id[Thread_id][msg_id] = 0;
											message_read_status = true;
											response_temp = Connections.expected_response[Thread_id][msg_id];

											Connections.request_sent[Thread_id][msg_id] = null;
											Connections.expected_response[Thread_id][msg_id] = null;

										}

										/*
										 * if (Debug)
										 * System.out.println("ReadThread "
										 * +Thread_id+ "\t "+response_temp);
										 */

										if (Connections.trace_all)
											writInforTologs(request_temp,
													response_temp,
													string_response);
										else {
											/*
											 * Log only failed(BAD) test
											 * cases(Responses)
											 */
											if (response_temp == null) {
												// System.out.println("How response-temp is null in Thread : "+Thread_id
												// +" Packet "+msg_id);
											} else {
												if (!(string_response
														.startsWith(response_temp))) {
													writInforTologs(
															request_temp,
															response_temp,
															string_response);
												}
											}
										}

										if (Connections.Debug) {
											// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
											// System.out.println("Read Thread "+Thread_id+" Request : "+request_temp);//response_list.isFull());
										}

										return true;
									}
								}
							} else if (protocol.equals("PROT_RAC")) /*
																	 * Response
																	 * Handling
																	 * for
																	 * Thales
																	 * Host
																	 * Functions
																	 * with
																	 * Secure
																	 * Channel
																	 * Disabled
																	 */
							{
								byte[] resp_header = new byte[2];
								short resp_len = 0;
								int header_bytes = 0, msg_bytes = 0;
								String response_temp = "";
								String request_temp = "";
								String msgidentifier = "";

								/*
								 * Read & Parse header (2 bytes) for reading
								 * Response length
								 */
								synchronized (Connections.Sockets[Thread_id]) {
									header_bytes = buffered_datain.read(
											resp_header, 0, resp_header.length);
								}

								resp_len = (short) ((resp_header[0] << 8) | (resp_header[1]));// Short.parseShort(str_resp_len,16);
																								// //(short)((resp_header[0]<<8)
																								// |
																								// (resp_header[1]));

								if (resp_len > 0) {
									response = new byte[resp_len];
									synchronized (Connections.Sockets[Thread_id]) {
										msg_bytes = buffered_datain.read(
												response, 0, response.length);
									}

									if ((new String(convertHexToString(
											response, response.length))) != null) {
										string_response = convertHexToString(
												response, response.length);

										if (request_loop < request_index) {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */
											msg_id = Integer
													.parseInt(convertStringToAscii(string_response
															.substring(0, 4)));

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];

											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 8 bytes Message Identifier
											 */
											/*
											 * 1. Fetch Message Id and assign to
											 * new Expected Response(initially
											 * ""): first 4 bytes of Expected
											 * Response (Ascii Hex String)
											 */
											response_temp = string_response
													.substring(0, 4);

											int temp = Thread_id;

											/*
											 * 2. Append 4 bytes of Thread_id to
											 * Expected Response
											 */
											String str_thd_id = "";
											int len = ("" + Thread_id).length();

											for (int i = 0; i < len; i++) {
												str_thd_id = ((temp % 10) + 30)
														+ str_thd_id;
												;
												temp = temp / 10;
											}

											len = str_thd_id.length();

											/*
											 * jugaad for extra 0's coming for
											 * threadid<9
											 */
											if (Thread_id > 9)// &&(Thread_id<100))
												len--;

											for (int i = 0; i < 5 - len; i++) {
												str_thd_id = "30" + str_thd_id;
											}

											/* for Thread id >99 */
											if (Thread_id > 99)
												str_thd_id = "30" + str_thd_id;

											response_temp += str_thd_id;

											msgidentifier = response_temp;

											/*
											 * Convert Expected Response
											 * prepared from Hex String to Ascii
											 * values
											 */
											response_temp = convertStringToAscii(response_temp);

											/*
											 * Read & parse Request string for
											 * "x__" values to convert them to
											 * Ascii
											 */
											while (request_temp.indexOf("x") > -1) {
												String str_temp = request_temp
														.substring(
																(request_temp
																		.indexOf("x") + 1),
																(request_temp
																		.indexOf("x") + 3));
												String str_to_replace = request_temp
														.substring(
																request_temp
																		.indexOf("x"),
																request_temp
																		.indexOf("x") + 3);
												str_temp = convertStringToAscii(str_temp);
												request_temp = request_temp
														.replace(
																str_to_replace,
																str_temp);

											}

											request_temp = (convertStringToAscii(Connections.request_sent[Thread_id][msg_id])
													.substring(2));

											String resp_process = response_list[request_loop]
													.substring(6);

											/*
											 * Read & parse Response string for
											 * "x__" values to convert them to
											 * Ascii
											 */
											while (resp_process.indexOf("x") > -1) {
												String str_temp = resp_process
														.substring(
																(resp_process
																		.indexOf("x") + 1),
																(resp_process
																		.indexOf("x") + 3));
												String str_to_replace = resp_process
														.substring(
																resp_process
																		.indexOf("x"),
																resp_process
																		.indexOf("x") + 3);
												str_temp = convertStringToAscii(str_temp);
												resp_process = resp_process
														.replace(
																str_to_replace,
																str_temp);

											}

											response_temp += resp_process;
										}

										/*
										 * commented string_response =
										 * response_msgidentifier +
										 * (convertStringToAscii
										 * (string_response )).substring(4);
										 */
										string_response = convertStringToAscii(string_response);

										/*
										 * Read 4 bytes Message Identifier from
										 * Request Sent corresponding to
										 * msg_id(function count)
										 */

										String request_msgidentifier = Connections.request_sent[Thread_id][msg_id]
												.substring(4, 12);

										request_temp = request_temp.trim();
										response_temp = response_temp
												.substring(6);

										/*
										 * Append Message Identifier to Expected
										 * Response
										 */
										response_temp = convertStringToAscii(msgidentifier)
												+ response_temp;

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
										request_loop++;

										if (request_loop >= request_index) {
											request_loop = 0;
										}

										if (Connections.Debug) {
											// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
										}
										return true;

									}
								}
							}
						}
						synchronized (Connections.Sockets[Thread_id]) {
							buffered_datain = null;
						}
						// System.out.println("message_read_status: "+message_read_status);

					}
				}
				// System.out.println("message_read_status: "+message_read_status);

				// }

			} catch (NullPointerException e) {
				if (Debug) {
					System.out.println("Thread: " + Thread_id
							+ " ReadInputstream ERROR.receive Null  : "
							+ e.toString());// /* .getMessage()*/);// +
											// " - Disconnected : " +
											// Connections.hip);
					if (protocol.equals("PROT_SOH")) {
						System.out.println("Thread_id:" + Thread_id
								+ " msg_id:" + msg_id + /*
														 * " Header:"+
														 * convertHexToString
														 * (resp_header,
														 * resp_header .length)+
														 */" Response:"
								+ string_response);
					} else if (protocol.equals("PROT_RAC"))
						System.out.println("Thread_id:"
								+ Thread_id
								+ " msg_id:"
								+ msg_id
								+ /*
								 * " Header:"+convertHexToString(
								 * resp_header,resp_header.length)+
								 */" Request:"
								+ Connections.request_sent[Thread_id][msg_id]
										.substring(4) + "\n Response:"
								+ convertStringToAscii(string_response));
				}
				e.printStackTrace();
			} catch (ArrayIndexOutOfBoundsException e) {
				if (Debug)
					System.out
							.println("Thread_id: "
									+ Thread_id
									+ " - Buffer Empty: ReadInputstream ERROR.receive ArrayIndexoutofbound  : "
									+ e.toString());// /*
													// .getMessage()*/);// +
													// " - Disconnected : "
													// + Connections.hip);
			} catch (Exception e) {
				if (!Connections.secure_channel) {
					if (!(mechanism.equals("Transient"))) 
					{
						if ((Connections.Sockets[Thread_id] != null) || (Connections.Sockets[Thread_id].isClosed()==false)) {
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
				Date date = new Date();
				if (Debug) {
					System.out.println("Msg Id: " + msg_id);// +" Request:"+convertStringToAscii(Connections.request_sent[Thread_id][msg_id]).substring(2));
					if (protocol.equals("PROT_SOH"))
						System.out.println("Thread_id:" + Thread_id
								+ " msg_id:" + msg_id + /*
														 * " Header:"+
														 * convertHexToString
														 * (resp_header,
														 * resp_header .length)+
														 */"\n Response:"
								+ string_response);
					else if (protocol.equals("PROT_RAC"))
						System.out.println(date.toString() + "Thread_id:"
								+ Thread_id + /*
											 * " Header"+convertHexToString(
											 * resp_header,2) +
											 */"Response:"
								+ convertStringToAscii(string_response));
				}

				if (Debug)
					System.out.println("Thread: " + Thread_id + " READ ERROR ["
							+ new Date().toString() + "] : " + e.toString()
							+ "\n");// /* .getMessage()*/);//
									// +
									// " - Disconnected : "
									// + Connections.hip);
				e.printStackTrace();

			}

		}

		return false;
	}

	/*
	 * Read Response from Input Stream & Validating Response used for Connection
	 * Pooling
	 */
	public boolean getresponse_pool() throws Exception {
		int timeout = 100;
		int data_received;
		String string_response = "";

		readres: {
			data_received = 0;

			try {
				synchronized (socketfrompool) {
					if (socketfrompool.isConnected()) {
						/* Read data from Socket Input Stream to Buffered Reader */
						buffered_datain = new DataInputStream(
								socketfrompool.getInputStream());
						data_received = buffered_datain.available();

						/*
						 * Check for Data Availability in Response DataStream
						 * from Luna EFT
						 */
						while (timeout > 0 && data_received <= 0) {
							timeout--;
							break readres;
						}

						/* No Data received */
						if (timeout == 0) {
							// timeout = 100;
							if (Debug)
								System.out.println("No Data received.");
							// return false;
						}

						if (data_received > 0) {
							timeout = 100;

							/*
							 * Response Handling for Luna EFT Host Functions
							 * with Secure Channel Disabled
							 */
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
								header_bytes = buffered_datain.read(
										resp_header, 0, resp_header.length);
								resp_len = (short) ((resp_header[4] << 8) | (resp_header[5]));

								if (resp_len > 0) {
									response = new byte[resp_len];

									msg_bytes = buffered_datain.read(response,
											0, response.length);

									if ((new String(convertHexToString(
											response, response.length))) != null) {

										string_response = convertHexToString(
												response, response.length)
												.trim();
										if (request_loop < request_index) {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */
											msg_id = Integer
													.parseInt(string_response
															.substring(6, 12));

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];
											request_temp = Connections.request_sent[Thread_id][msg_id];

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

											/*
											 * For Luna EFT functions, Function
											 * Count ranges from 0 to 9999
											 */
											if (msg_id > (Connections.max_buffer_size - 1))
												msg_id = 0;
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
										request_loop++;

										if (request_loop >= request_index) {
											request_loop = 0;
										}

										if (Connections.Debug) {
											// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
											// System.out.println("Read Thread "+Thread_id+" Request : "+request_temp);//response_list.isFull());
										}

										return true;
									}
								}
							}
							/*
							 * Response Handling for Thales Host Functions with
							 * Secure Channel Disabled
							 */
							else if (protocol.equals("PROT_RAC")) {
								byte[] resp_header = new byte[2];
								short resp_len = 0;
								int header_bytes = 0, msg_bytes = 0;
								String response_temp = "";
								String request_temp = "";
								String msgidentifier = "";

								/*
								 * Read & Parse header (2 bytes) for reading
								 * Response length
								 */
								header_bytes = buffered_datain.read(
										resp_header, 0, resp_header.length);
								resp_len = (short) ((resp_header[0] << 8) | (resp_header[1]));// Short.parseShort(str_resp_len,16);
																								// //(short)((resp_header[0]<<8)
																								// |
																								// (resp_header[1]));

								if (resp_len > 0) {
									response = new byte[resp_len];
									msg_bytes = buffered_datain.read(response,
											0, response.length);

									if ((new String(convertHexToString(
											response, response.length))) != null) {
										string_response = convertHexToString(
												response, response.length);

										if (request_loop < request_index) {
											/*
											 * Fetched 2 byte Function
											 * Count(first two bytes of Function
											 * Identifier) from Response
											 * received to be used for Response
											 * Validation
											 */
											msg_id = Integer
													.parseInt(convertStringToAscii(string_response
															.substring(0, 4)));

											/*
											 * Use Function Identifier to read
											 * Request & Response from buffers
											 * using Function Count(msg_id)
											 */
											request_loop = Connections.request_sent_id[Thread_id][msg_id];

											/*
											 * Prepare Expected Response for
											 * Response Validation that contains
											 * 4 byte Function Identifier
											 */
											/*
											 * 1. Fetch Function Name and append
											 * to new Expected
											 * Response(initially ""): first 2
											 * bytes of Expected Response (Ascii
											 * Hex String)
											 */
											response_temp = string_response
													.substring(0, 4);

											int temp = Thread_id;

											/*
											 * 2. Append 2 bytes of Thread_id to
											 * Expected Response
											 */
											String str_thd_id = "";// +Thread_id;
											int len = ("" + Thread_id).length();

											for (int i = 0; i < len; i++) {
												str_thd_id = ((temp % 10) + 30)
														+ str_thd_id;
												;
												temp = temp / 10;
											}

											len = str_thd_id.length();

											/*
											 * jugaad for extra 0's coming for
											 * threadid<9
											 */
											if (Thread_id > 9)// &&(Thread_id<100))
												len--;

											for (int i = 0; i < 5 - len; i++) {
												str_thd_id = "30" + str_thd_id;
											}

											/* for Thread id >99 */
											if (Thread_id > 99)
												str_thd_id = "30" + str_thd_id;

											response_temp += str_thd_id;

											msgidentifier = response_temp;

											/*
											 * Convert Expected Response
											 * prepared from Hex String to Ascii
											 * values
											 */
											response_temp = convertStringToAscii(response_temp);

											/*
											 * Read & parse Request string for
											 * "x__" values to convert them to
											 * Ascii
											 */
											while (request_temp.indexOf("x") > -1) {
												String str_temp = request_temp
														.substring(
																(request_temp
																		.indexOf("x") + 1),
																(request_temp
																		.indexOf("x") + 3));
												String str_to_replace = request_temp
														.substring(
																request_temp
																		.indexOf("x"),
																request_temp
																		.indexOf("x") + 3);
												str_temp = convertStringToAscii(str_temp);
												request_temp = request_temp
														.replace(
																str_to_replace,
																str_temp);

											}

											request_temp = (convertStringToAscii(Connections.request_sent[Thread_id][msg_id])
													.substring(2));

											String resp_process = response_list[request_loop]
													.substring(6);

											/*
											 * Read & parse Response string for
											 * "x__" values to convert them to
											 * Ascii
											 */
											while (resp_process.indexOf("x") > -1) {
												String str_temp = resp_process
														.substring(
																(resp_process
																		.indexOf("x") + 1),
																(resp_process
																		.indexOf("x") + 3));
												String str_to_replace = resp_process
														.substring(
																resp_process
																		.indexOf("x"),
																resp_process
																		.indexOf("x") + 3);
												str_temp = convertStringToAscii(str_temp);
												resp_process = resp_process
														.replace(
																str_to_replace,
																str_temp);

											}

											response_temp += resp_process;
										}

										/*
										 * commented string_response =
										 * response_msgidentifier +
										 * (convertStringToAscii
										 * (string_response)).substring(4);
										 */
										string_response = convertStringToAscii(string_response);

										/*
										 * Read 4 bytes Function Identifier from
										 * Request Sent corresponding to
										 * msg_id(function count)
										 */
										String request_msgidentifier = Connections.request_sent[Thread_id][msg_id]
												.substring(4, 12);

										request_temp = request_temp.trim();
										response_temp = response_temp
												.substring(6);

										/*
										 * Append msgidentifier = Function Name
										 * + Function Identifier to Expected
										 * Response
										 */
										response_temp = convertStringToAscii(msgidentifier)
												+ response_temp;

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
										request_loop++;

										if (request_loop >= request_index) {
											request_loop = 0;
										}

										if (Connections.Debug) {
											// System.out.println("Thread "+Thread_id+" Response : "+string_response);//response_list.isFull());
										}
										return true;
									}
								}
							}
						}
					}
				}
				buffered_datain = null;
			} catch (NullPointerException e) {
				Date date = new Date();
				if (Debug)
					System.out.println(date.toString() + " ReadThread "
							+ Thread_id
							+ "ReadInputstream ERROR.receive Null  : "
							+ e.toString());// /* .getMessage()*/);// +
											// " - Disconnected : " +
											// Connections.hip);
				break readres;
			} catch (ArrayIndexOutOfBoundsException e) {
				Date date = new Date();
				if (Debug)
					System.out
							.println(date.toString()
									+ " ReadThread "
									+ Thread_id
									+ " - Buffer Empty: ReadInputstream ERROR.receive ArrayIndexoutofbound  : "
									+ e.toString());// /* .getMessage()*/);// +
													// " - Disconnected : " +
													// Connections.hip);
				break readres;
			} catch (Exception e) {
				Date date = new Date();
				if (Debug)
					System.out.println(date.toString() + " ReadThread "
							+ Thread_id + "Copy_to_buffer ERROR.receive : "
							+ e.toString() + "\n");// /* .getMessage()*/);// +
													// " - Disconnected : " +
													// Connections.hip);
				e.printStackTrace();
			}
		}
		return false;
	}

	/* Convert from Hex-decimal to String */
	public static String convertHexToString(byte[] baInput, int iLen) {
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

	/* Convert from Ascii to Hex */
	private static String asciiToHex(String asciiValue) {
		char[] chars = asciiValue.toCharArray();
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < chars.length; i++) {
			hex.append(Integer.toHexString((int) chars[i]));
		}
		return hex.toString();
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


	/* Convert from Ascii to String */
	public String convertAsciiToString(final String strAsciiInput) {
		char[] chars = strAsciiInput.toCharArray();

		String hex = new String();
		for (int i = 0; i < chars.length; i++) {
			hex += Integer.toHexString((int) chars[i]);
		}

		return hex.toString();
	}

	/* Convert from String to Hex-decimal */
	public static byte[] convertStringToHex(final String strInput) {
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

	/* Create file(if doesn't exist), open it & get file handle(Buffered Writer) */
	public void getFileHandle() {
		gethandle: try {
			String filename = "Logs\\TraceLog_Thread_" + Thread_id + ".TXT";
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
			// filelogger.write("Time: " + date.getTime());
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
