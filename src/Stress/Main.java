/** Main Class**/

package Stress;

public class Main {
	static String ip = "";
	static String port = "";
	static int connections_count = 0;

	public static void main(String[] args) {
		try {
			/*
			 * Spawns Connection Thread that further spawns read-write threads
			 * per connection
			 */
			new Thread(new Connections()).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

@SuppressWarnings("serial")
class InvalidValuesException extends Exception {

	public InvalidValuesException(String message) {
		super(message);
	}

}