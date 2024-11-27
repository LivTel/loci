// LociTCPServer.java
// $Header$
package ngat.loci;

import java.lang.*;
import java.io.*;
import java.net.*;

import ngat.net.*;

/**
 * This class extends the TCPServer class for the Loci application.
 * @author Chris Mottram
 * @version $Revision: LociTCPServer.java $
 */
public class LociTCPServer extends TCPServer
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Field holding the instance of Loci currently executing, so we can pass this to spawned threads.
	 */
	private Loci loci = null;

	/**
	 * The constructor.
	 * @param name The name of the server thread.
	 * @param portNumber The port number to wait for connections on.
	 */
	public LociTCPServer(String name,int portNumber)
	{
		super(name,portNumber);
	}

	/**
	 * Routine to set this objects pointer to the loci object.
	 * @param o The loci object.
	 */
	public void setLoci(Loci o)
	{
		this.loci = o;
	}

	/**
	 * This routine spawns threads to handle connection to the server. This routine
	 * spawns LociTCPServerConnectionThread threads.
	 * The routine also sets the new threads priority to higher than normal. This makes the thread
	 * reading it's command a priority so we can quickly determine whether the thread should
	 * continue to execute at a higher priority.
	 * @see LociTCPServerConnectionThread
	 */
	public void startConnectionThread(Socket connectionSocket)
	{
		LociTCPServerConnectionThread thread = null;

		thread = new LociTCPServerConnectionThread(connectionSocket);
		thread.setLoci(loci);
		thread.setPriority(loci.getStatus().getThreadPriorityInterrupt());
		thread.start();
	}
}
