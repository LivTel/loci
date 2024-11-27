// LociTCPClientConnectionThread.java
// $Header$
package ngat.loci;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.Date;

import ngat.net.*;
import ngat.message.base.*;

/**
 * The LociTCPClientConnectionThread extends TCPClientConnectionThread. 
 * It implements the generic ISS/DP(RT) instrument command protocol with multiple acknowledgements. 
 * The instrument starts one of these threads each time
 * it wishes to send a message to the ISS/DP(RT).
 * @author Chris Mottram
 * @version $Revision: LociTCPClientConnectionThread.java $
 */
public class LociTCPClientConnectionThread extends TCPClientConnectionThreadMA
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The commandThread was spawned by the Loci to deal with a Loci command request. 
	 * As part of the running of
	 * the commandThread, this client connection thread was created. We need to know the server thread so
	 * that we can pass back any acknowledge times from the ISS/DpRt back to the Loci client (ISS/IcsGUI etc).
	 */
	private LociTCPServerConnectionThread commandThread = null;
	/**
	 * The Loci object.
	 */
	private Loci loci = null;
	
	/**
	 * A constructor for this class. Currently just calls the parent class's constructor.
	 * @param address The internet address to send this command to.
	 * @param portNumber The port number to send this command to.
	 * @param c The command to send to the specified address.
	 * @param ct The Loci command thread, the implementation of which spawned this command.
	 */
	public LociTCPClientConnectionThread(InetAddress address,int portNumber,COMMAND c,
					     LociTCPServerConnectionThread ct)
	{
		super(address,portNumber,c);
		commandThread = ct;
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
	 * This routine processes the acknowledge object returned by the server. It
	 * prints out a message, giving the time to completion if the acknowledge was not null.
	 * It sends the acknowledgement to the Loci client for this sub-command of the command,
	 * so that Loci's client does not time out if,say, a zero is returned.
	 * @see LociTCPServerConnectionThread#sendAcknowledge
	 * @see #commandThread
	 */
	protected void processAcknowledge()
	{
		if(acknowledge == null)
		{
			loci.error(this.getClass().getName()+":processAcknowledge:"+
				   command.getClass().getName()+":acknowledge was null.");
			return;
		}
	// send acknowledge to Loci client.
		try
		{
			commandThread.sendAcknowledge(acknowledge);
		}
		catch(IOException e)
		{
			loci.error(this.getClass().getName()+":processAcknowledge:"+
				   command.getClass().getName()+":sending acknowledge to client failed:",e);
		}
	}

	/**
	 * This routine processes the done object returned by the server. 
	 * It prints out the basic return values in done.
	 */
	protected void processDone()
	{
		ACK acknowledge = null;

		if(done == null)
		{
			loci.error(this.getClass().getName()+":processDone:"+
				   command.getClass().getName()+":done was null.");
			return;
		}
	// construct an acknowledgement to sent to the Loci client to tell it how long to keep waiting
	// it currently returns the time the Loci origianally asked for to complete this command
	// This is because the Loci assumed zero time for all sub-commands.
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(commandThread.getAcknowledgeTime());
		try
		{
			commandThread.sendAcknowledge(acknowledge);
		}
		catch(IOException e)
		{
			loci.error(this.getClass().getName()+":processDone:"+
				   command.getClass().getName()+":sending acknowledge to client failed:",e);
		}
	}
}
