/***********************************Connection Thread *************************************\\
1. Read Config.txt
2. Read test scripts
3. Create & Monitor Read/Write Threads for each connection mechanism
\\******************************************************************************************/

/* Rev 1.0 - 18.06.2018
 * ------------------
 * Most of the commented code is kept for future reference.
 * Currently, Stress tool supports only Single threaded model for Host Communication.
 * In this model, Single thread sends request and waits for response before sending next request.
 */
package Stress;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.locks.*;

/*Import Secure Channel Related Packages*/
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/*Import External Library Classes for Connection Pooling*/
import edu.emory.mathcs.util.net.Connection;
import edu.emory.mathcs.util.net.ConnectionPool;

public class Connections implements Runnable {
	static Socket Sockets[];
	static SSLSocket SSLSockets[];
	static boolean connstatus[];
	static String mechanism = "";
	static boolean secure_channel = false;
	static int max_buffer_size = 10;// 10000;//000;
	static int request_sent_id[][];
	static String request_sent[][];
	static String expected_response[][]; // Rajat added 20.07.2016
	static int buffer_size[];
	static int throttle = 0;
	static String packetpersec = "0";

	static String hip = "";
	static String hport = "";
	static boolean trace_all = false;
	static int delay = 0;
	static int soc_timeout = 100;
	static int request_per_thread = 0;
	static boolean Debug = false;
	
	static int intLock = 0;

	int connections_count = 0;
	int max_connections_count = 0;
	String file_dir = "";
	int thread_count = 0;
	String file_name = "";

	int filelist_index = 0;
	String request[] = new String[100];
	String response[] = new String[100];
	int request_index = 0;

	ConnectionPool connpool;
	static boolean ThreadCompleted[];
	long expirationTimeout = 10000;
	static String protocol = "";
	static boolean start_transient_send[];

	static ReentrantLock lock[];
	static Condition notFull[];
	static int LastIndex[];

	static ReentrantLock lock_throttle;
	static Condition condition_throttle;

	static String cipherSuite = "";
	static String cert_dir = "";
	static String privatekey = "";
	static String hostcert = "";
	static String hsmcert = "";
	static String keyfile = "";
	static String keyfile_der = "";
	static String certfile = "";
	static String defaultalias = "importkey";
	static String hsmcertfile = "";
	static ImportKey importkey;
	static KeyStore ks_host;
	static KeyStore ts_hsm;
	public static SSLSocketFactory socketfactory[];
	static final short Sfnt_Msg_Header_Length = 6;
	static final short Sfnt_Msg_Identifier_Length = 5;
	static final short Sfnt_Msg_ID = 6;
	static final short Sfnt_Thread_ID = 4;

	/* Read Configuration file, Test Scripts & initialize variables */
	Connections() throws InterruptedException {
		try {
			readConfiguration(); /* Reads Configuration file Config.txt */
			readRequest(); /* Read Test Scripts. */

			connections_count = max_connections_count;

			if (Debug)
				System.out.println("Buffer_Size : " + max_buffer_size);
			if (mechanism.equals("Socket_Pool")) {
				request_sent_id = new int[thread_count][max_buffer_size];
				request_sent = new String[thread_count][max_buffer_size];
			} else {
				Sockets = new Socket[connections_count];
				if (secure_channel) {
					socketfactory = new SSLSocketFactory[connections_count];
					SSLSockets = new SSLSocket[connections_count];
				}

				connstatus = new boolean[connections_count];
				ThreadCompleted = new boolean[connections_count];
				request_sent_id = new int[connections_count][max_buffer_size];
				request_sent = new String[connections_count][max_buffer_size];
				expected_response = new String[connections_count][max_buffer_size]; // Rajat
																					// added
																					// 20.07.2016

				buffer_size = new int[connections_count];
				lock = new ReentrantLock[connections_count];
				notFull = new Condition[connections_count];
				LastIndex = new int[connections_count];

				lock_throttle = new ReentrantLock();
				condition_throttle = lock_throttle.newCondition();

				System.out.println("thread_count : " + connections_count);
				for (int t_id = 0; t_id < connections_count; t_id++) {
					buffer_size[t_id] = 0;
					lock[t_id] = new ReentrantLock();
					notFull[t_id] = lock[t_id].newCondition();
					LastIndex[t_id] = -1;
				}
			}
		} catch (NullPointerException ne) {
			if (Debug)
				ne.printStackTrace();
			throw ne;
		} catch (Exception e) {
			if (Debug)
				e.printStackTrace();

			throw e;
		}
	}

	public void run() {
		/* Spawn Write-Request & Read-Response Thread for each stress mechanism */
		/*switch (mechanism) {
		case "Persistent": /*
							 * Persistent Connections : Support connections
							 * with/without secure channel
							 */
			try {
				//ReadThread readthread[] = new ReadThread[connections_count];
				//WriteThread writethread[] = new WriteThread[connections_count];
				SingleThread singlethread[] = new SingleThread[connections_count];

				for (int i = 0; i < connections_count; i++) {
					if (initialize_Socket(hip, hport, i)) {
						connstatus[i] = true;
						Thread.sleep(2000);

						/*if (!mechanism.equals("Persistent")) {
						//writethread[i] = new WriteThread(i, request, response, request_index); */
						singlethread[i] = new SingleThread(i, request, response, request_index);
					/* } else {
						if(secure_channel)
						{
							singlethread[i] = new SingleThread(i, request, response, request_index);
						}
						else
						{							
							writethread[i] = new WriteThread(i, request,
									response, request_index); // Rajat added
																// response as
																// parameter
																// 20.07.2016
							// writethread[i] = new WriteThread(i, request,
							// request_index);
							readthread[i] = new ReadThread(i, request,
									response, request_index);

							readthread[i].setPriority(1);
							writethread[i].setPriority(2);
						}

					}*/
					}
				}

				long lastSec = 0;

				/*
				 * Timer Implementation for Monitoring packets sent to HSM per
				 * second. Resets counter to zero after each second.
				 */
				while (true) {
					long sec = System.currentTimeMillis() / 1000;
					if (sec != lastSec) {
						lastSec = sec;
						synchronized (packetpersec) {
							/*
							 * lock_throttle.lock();
							 * 
							 * try {
							 */
							// System.out.println("Seconds :"+lastSec+"\nBefore Set : "+Integer.parseInt(packetpersec));
							System.out.println(" Connections.packetpersec:"
									+ Connections.packetpersec
									+ " Connections.throttle"
									+ Connections.throttle);
							packetpersec = "0";

							// condition_throttle.signalAll();
							// System.out.println("After Set : "+Integer.parseInt(packetpersec));
							/*
							 * } finally { lock_throttle.unlock(); }
							 */
						}

					}
				}

			}

			catch (Exception e) {
				e.printStackTrace();
				if (Debug)
					System.out.println("Connections ERROR.run : "
							+ e.toString());
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException localInterruptedException) {
				}
			}
			//break;

		/*case "Transient": /*
						 * Transient Connections : Support connections
						 * with/without secure channel
						 */
			/*try {
				ReadThread readthread[] = new ReadThread[connections_count];
				WriteThread writethread[] = new WriteThread[connections_count];
				start_transient_send = new boolean[connections_count];
				for (int i = 0; i < connections_count; i++) {
					if (initialize_Socket(hip, hport, i)) {
						connstatus[i] = true;
						ThreadCompleted[i] = true;

						if (secure_channel) {
							start_transient_send[i] = false;
							writethread[i] = new WriteThread(i, request,
									response, request_index);
						} else {
							start_transient_send[i] = false;
							writethread[i] = new WriteThread(i, request,
									request_index);

							readthread[i] = new ReadThread(i, request,
									response, request_index);
						}
					}
				}
				for (int i = 0; i < connections_count; i++) {
					writethread[i].join();
					readthread[i].join();
				}
			}

			catch (Exception e) {
				if (Debug)
					System.out.println("Connections ERROR.run : "
							+ e.toString());
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException localInterruptedException) {
				}
			}
			break;

		case "Socket_Pool":/*
							 * Connection Pooling : Support connections without
							 * secure channel only. Secure Channel not supported
							 */
			/*try {
				connpool = new ConnectionPool(hip, Integer.parseInt(hport),
						expirationTimeout, connections_count);

				ReadThread readthread[] = new ReadThread[thread_count];
				WriteThread writethread[] = new WriteThread[thread_count];

				/* Initial thread creation */
				/*for (int i = 0; i < connections_count; i++) {
					Connection conn = connpool.getConnection();
					Socket socket = conn.getSocket();
					if (socket != null) {
						writethread[i] = new WriteThread(i, socket, conn,
								request, request_index);
						readthread[i] = new ReadThread(i, socket, conn,
								request, response, request_index);
					}
				}

				/*
				 * Once initial request processing cycle is completed. Spawns
				 * new threads and monitor them infinitely
				 */
				/*while (true) {
					for (int i = 0; i < thread_count; i++) {
						Connection conn = connpool.getConnection();
						Socket socket = conn.getSocket();

						checkthread: if ((readthread[i] != null)
								&& (writethread[i] != null)) {
							if ((readthread[i].isAlive() == true)
									|| (writethread[i].isAlive() == true)) {
								i++;

								if (i >= thread_count)
									i = 0;
								break checkthread;
							}
						}

						writethread[i] = new WriteThread(i, socket, conn,
								request, request_index);
						readthread[i] = new ReadThread(i, socket, conn,
								request, response, request_index);
					}
				}

			}

			catch (Exception e) {
				if (Debug)
					System.out.println("Connections ERROR.run : "
							+ e.toString());
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException localInterruptedException) {
				}
			}
			break;

		default:
			try {
				if (Debug)
					System.out.println("Invalid Mechanism ");
				throw new InvalidValuesException("Invalid Mechanism");
			}

			catch (Exception e) {
				if (Debug)
					System.out.println("Connections ERROR.run : "
							+ e.toString());
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException localInterruptedException) {
				}
			}
			break;
		}*/
	}

	/* Read Config.txt, parse parameters & store them into variables */
	public void readConfiguration() {
		Scanner scanner;

		try {
			String string_config, temp;
			FileReader fr = new FileReader("Config.txt");
			BufferedReader br = new BufferedReader(fr);

			while ((string_config = br.readLine()) != null) {
				if (string_config.indexOf(";") > -1) {
					temp = string_config.substring(string_config.indexOf(";"));
					string_config = string_config.replace(temp, "");
				}
				scanner = new Scanner(string_config);

				if (string_config.indexOf("Debug") > -1) {
					scanner.next();

					if (scanner.next().toString().trim().equals("ON"))
						Debug = true;
					else
						Debug = false;

					if (Debug)
						System.out.println("Debug Status : " + Debug);
				}

				if (string_config.indexOf("protocol") > -1) {
					scanner.next();

					protocol = scanner.next().toString().trim();

					if (Debug)
						System.out.println("Protocol : " + protocol);
				}

				if (string_config.indexOf("port") > -1) {
					scanner.next();

					hport = scanner.next().toString().trim();

					if (Debug)
						System.out.println("Port : " + hport);
				}

				if (scanner.hasNext("server")) {
					scanner.next();

					hip = scanner.next().toString().trim();

					if (Debug)
						System.out.println("Server Name : " + hip);
				}

				if (scanner.hasNext("path")) {
					scanner.next();

					file_dir = scanner.next().toString().trim();

					if (Debug)
						System.out.println("File Directory : " + file_dir);
				}

				if (scanner.hasNext("Mechanism")) {
					scanner.next();

					mechanism = scanner.next().toString().trim();

					if (Debug)
						System.out.println("Mechanism : " + mechanism);
				}

				if (scanner.hasNext("Secure_Channel")) {
					scanner.next();

					if (scanner.next().toString().trim().equals("ON"))
						secure_channel = true;
					else
						secure_channel = false;

					if (Debug)
						System.out.println("Secure Channel Status : "
								+ secure_channel);
				}

				if (string_config.indexOf("Buffer_Size") > -1) {
					scanner.next();

					max_buffer_size = scanner.nextInt();

					if (Debug)
						System.out.println("Buffer_Size : " + max_buffer_size);
				}

				if (string_config.indexOf("Throttle") > -1) {
					scanner.next();

					throttle = scanner.nextInt();

					if (Debug)
						System.out.println("Throttle : " + throttle);
				}

				if (scanner.hasNext("Trace")) {
					scanner.next();

					if (scanner.next().toString().trim().equals("ALL"))
						trace_all = true;
					else
						trace_all = false;

					if (Debug)
						System.out.println("Trace_All Status : " + trace_all);
				}

				if (string_config.indexOf("connections") > -1) {
					scanner.next();

					max_connections_count = scanner.nextInt();

					if ((max_connections_count < 1)
							|| (max_connections_count > 128)) {
						scanner.close();
						throw new InvalidValuesException(
								"Invalid connections_count");
					}

					if (Debug)
						System.out.println("Max Connections : "
								+ max_connections_count);
				}

				if (string_config.indexOf("Thread_Count") > -1) {
					scanner.next();

					thread_count = scanner.nextInt();

					if (Debug)
						System.out.println("Thread Count : " + thread_count);
				}

				if (string_config.indexOf("Delay") > -1) {
					scanner.next();

					delay = scanner.nextInt();

					if (Debug)
						System.out.println("Connection Delay : " + delay);
				}

				if (string_config.indexOf("Timeout") > -1) {
					scanner.next();

					soc_timeout = scanner.nextInt();

					if (Debug)
						System.out.println("Socket Timeout : " + soc_timeout);
				}

				if (string_config.indexOf("Requestperthread") > -1) {
					scanner.next();

					request_per_thread = scanner.nextInt();

					if (Debug)
						System.out.println("Requests Per Thread : "
								+ request_per_thread);
				}

				/*
				 * If secure channel is enabled, read Secure Channel
				 * Configuration Parameters
				 */
				if (secure_channel) {
					if (scanner.hasNext("CertPath")) {
						scanner.next();

						cert_dir = scanner.next().toString().trim();

						if (Debug)
							System.out
									.println("Secure Channel Files Directory : "
											+ cert_dir);
					}

					if (string_config.indexOf("Private_Key") > -1) {
						scanner.next();
						privatekey = scanner.next().toString().trim();

						if (Debug)
							System.out.println("Private Key : " + privatekey);
					}

					if (string_config.indexOf("HostCertificate") > -1) {
						scanner.next();
						hostcert = scanner.next().toString().trim();

						if (Debug)
							System.out
									.println("Host Certificate : " + hostcert);
					}

					if (string_config.indexOf("HSMCertficate") > -1) {
						scanner.next();
						hsmcert = scanner.next().toString().trim();

						if (Debug)
							System.out.println("HSM Certficate : " + hsmcert);
					}
					
					if (string_config.indexOf("CipherSuite") > -1) {
						scanner.next();
						cipherSuite = scanner.next().toString().trim();

						if (Debug)
							System.out.println("Cipher Suite : " + cipherSuite);
					}
				}

				/*
				 * Read Test Script File Name. Only one pacetest command is
				 * allowed.
				 */
				if (string_config.indexOf("pacetest") > -1) {
					scanner.next();
					file_name = scanner.next().toString().trim();

					if (Debug)
						System.out.println("Script Name : " + file_name);
					filelist_index++;
				}
				scanner.close();
			}
			br.close();
			fr.close();
		} catch (Exception e) {
			if (Debug)
				System.out.println("Config file Read Error");
			e.printStackTrace();
		}
	}

	/* Read test script, parse them & store into Request/Response arrays */
	public void readRequest() {
		FileReader fr;
		BufferedReader br;
		String string_read, path;
		int request_count = 0, response_count = 0;

		try {
			path = file_dir + "\\" + file_name;
			fr = new FileReader(path);
			br = new BufferedReader(fr);

			while ((string_read = br.readLine()) != null) {
				if (string_read.indexOf("REQUEST") > -1) {
					if (protocol.equals("PROT_SOH"))
						request[request_count] = string_read.substring(
								(string_read.indexOf("=") + 1)).toUpperCase();
					else if (protocol.equals("PROT_RAC"))
						request[request_count] = string_read
								.substring((string_read.indexOf("=") + 1));

					if (Debug)
						System.out.println("Request " + request[request_count]);
					request_count++;
					request_index++;
				}
				if (string_read.indexOf("EXPECTED") > -1) {
					if (protocol.equals("PROT_SOH"))
						response[response_count] = string_read.substring(
								(string_read.indexOf("=") + 1)).toUpperCase();
					else if (protocol.equals("PROT_RAC"))
						response[response_count] = string_read
								.substring((string_read.indexOf("=") + 1));

					if (Debug)
						System.out.println("Response "
								+ response[response_count]);
					response_count++;
				}

			}
		} catch (Exception e) {
			if (Debug)
				System.out.println("Request Read Error");
			e.printStackTrace();
		}
	}

	/* Create & initialize socket with/without secure channel */
	public static boolean initialize_Socket(String ip, String port, int sock_id) {
		try {
			Sockets[sock_id] = new Socket();

			Sockets[sock_id].setKeepAlive(true);

			if (secure_channel) {
				
					//Added Lock mechanism to handle connection requests from multiple threads. 
					while(intLock != 0)
					{
						Thread.sleep(10);
					}
				
					trySSLSocket:
					if(intLock == 0)
					{
						intLock++;
						// Sockets[sock_id].setKeepAlive(true);
						keyfile = cert_dir + "\\" + privatekey;
						certfile = cert_dir + "\\" + hostcert;
						hsmcertfile = cert_dir + "\\" + hsmcert;
						keyfile_der = cert_dir + "\\"
								+ privatekey.substring(0, privatekey.indexOf(".key"))
								+ ".der";
						Process p = Runtime.getRuntime().exec(
								"openssl pkcs8 -topk8 -nocrypt -outform der -in "
										+ "\"" + keyfile + "\" -out \"" + keyfile_der
										+ "\"");
						p.waitFor();
		
						if (p.exitValue() == 0) {
							importkey = new ImportKey(keyfile_der, certfile,
									hsmcertfile, defaultalias);
							ks_host = importkey.ks_host;
							ts_hsm = importkey.ts_hsm;
		
							try {
								SSLContext ctx = SSLContext.getInstance("TLSv1.2");
		
								TrustManager[] trustManagers = importkey
										.getTrustManagers(ts_hsm, "importkey");
								KeyManager[] keyManagers = importkey.getKeyManagers(
										ks_host, "importkey");
								ctx.init(keyManagers, trustManagers, null);
		
								socketfactory[sock_id] = (SSLSocketFactory) ctx
										.getSocketFactory();
		
								System.out.println("Preinitialization SocketFactory");
								SSLSockets[sock_id] = (SSLSocket) socketfactory[sock_id]
										.createSocket(ip, Integer.parseInt(port));
		
								SSLSockets[sock_id].setReceiveBufferSize(67108864);
		
								final String[] enabledCipherSuites = { Connections.cipherSuite };// TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
																											// //TLS_RSA_WITH_AES_256_GCM_SHA384
																											// //TLS_RSA_WITH_AES_128_GCM_SHA256
								
								System.out.println("Enabled ciphers: "
										+ Arrays.toString(SSLSockets[sock_id]
												.getEnabledCipherSuites()));
								
								try
								{
									SSLSockets[sock_id]
										.setEnabledCipherSuites(enabledCipherSuites);
								}
								catch(Exception e)
								{
									System.out.println("Exception occured : Unsupported Cipher");
									return false;
								}
		
		
								// Enable only one cipher suite
								// String enabledSuites[] = {
								// "SSL_ECDHE_RSA_WITH_AES_128_GCM_SHA256" };
								// SSLSockets[sock_id].setEnabledCipherSuites(enabledSuites);
								SSLSockets[sock_id].setKeepAlive(true);
								SSLSockets[sock_id].setSoTimeout(soc_timeout);
		
								System.out.println("SSLSocket Details : "
										+ /* SSL */SSLSockets[sock_id].toString()
										+ "  Cipher suite : "
										+ Arrays.toString(SSLSockets[sock_id]
												.getEnabledCipherSuites()));
							} catch (NullPointerException ne) {
								ne.printStackTrace();
							} catch (Exception e) {
								System.out.println(e);
							}
		
						} else {
							System.exit(0);
						}
						
						System.out.println("Lock value : "+intLock);
						intLock --;
					}
					else
					{
						while(intLock != 0)
						{
							Thread.sleep(10);
						}
						break trySSLSocket;
					}
			} else {
				InetAddress anetAdd = InetAddress.getByName(ip.trim());

				InetSocketAddress sockAddress = new InetSocketAddress(anetAdd,
						Integer.parseInt(port.trim()));

				Sockets[sock_id].setReceiveBufferSize(67108864);
				Sockets[sock_id].setSoTimeout(soc_timeout);

				Sockets[sock_id].connect(sockAddress, 10000);
				// System.out.println("Receive Buffer Size : " +
				// Sockets[sock_id].getReceiveBufferSize());
			}

			return true;
		} catch (Exception e) {
			if (Debug)
				System.out.println("ERROR.init : " + e.getMessage());
			return false;
		}
	}
}
