// ABORTImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.loci.ccd.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the ABORT command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: ABORTImplementation.java $
 */
public class ABORTImplementation extends CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public ABORTImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.ABORT&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.ABORT";
	}

	/**
	 * This method gets the ABORT command's acknowledge time. This takes the default acknowledge time to implement.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the ABORT command. 
	 * <ul>
	 * <li>We call sendAbortExposureCommand to send an "abortExposure" command to the loci-ctrl CCD Flask API.
	 * <li>We get the currently running thread from the status object.
	 * <li>If the currently running thread is non-null, we call setAbortProcessCommand to tell the
	 *     Java thread to abort itself at a suitable point.
	 * <li>We set up a successful ABORT_DONE to return.
	 * </ul>
	 * @param command The abort command.
	 * @return An object of class ABORT_DONE is returned.
	 * @see #sendAbortExposureCommand
	 * @see #status
	 * @see LociStatus#getCurrentThread
	 * @see LociTCPServerConnectionThread
	 * @see LociTCPServerConnectionThread#setAbortProcessCommand
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		ABORT_DONE abortDone = new ABORT_DONE(command.getId());
		LociTCPServerConnectionThread thread = null;

		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		try
		{
			sendAbortExposureCommand();
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+":Aborting exposure failed:",e);
			abortDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2400);
			abortDone.setErrorString(e.toString());
			abortDone.setSuccessful(false);
			return abortDone;
		}
	// tell the thread itself to abort at a suitable point
		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Tell thread to abort.");
		thread = (LociTCPServerConnectionThread)status.getCurrentThread();
		if(thread != null)
			thread.setAbortProcessCommand();
	// return done object.
		loci.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+command.getClass().getName()+
			  ":Abort command completed.");
		abortDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		abortDone.setErrorString("");
		abortDone.setSuccessful(true);
		return abortDone;
	}
	
	/**
	 * Send an "abortExposure" command to the loci-ctrl CCD Flask API.
	 * @exception Exception Thrown if an error occurs.
	 * @see #status
	 * @see ngat.loci.ccd.AbortExposureCommand
	 */
	protected void sendAbortExposureCommand() throws Exception
	{
		AbortExposureCommand abortExposureCommand = null;
		String ccdFlaskHostname = null;
		int ccdFlaskPortNumber;

		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendAbortExposureCommand:Started.");
		// get CCD Flask API connection data
		ccdFlaskHostname = status.getProperty("loci.flask.ccd.hostname");
		ccdFlaskPortNumber = status.getPropertyInteger("loci.flask.ccd.port_number");
		// setup abort command
		abortExposureCommand = new AbortExposureCommand();
		abortExposureCommand.setAddress(ccdFlaskHostname);
		abortExposureCommand.setPortNumber(ccdFlaskPortNumber);
		abortExposureCommand.run();
		if(abortExposureCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+":sendAbortExposureCommand:Abort failed:",
					    abortExposureCommand.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,"sendAbortExposureCommand:Finished with status: "+
			 abortExposureCommand.getReturnStatus()+
			 " and message:"+abortExposureCommand.getMessage()+".");
		// If the abort exposure command fails because the detector is in DRV_IDLE, for the purposes
		// of this command implementation treat this as a sucess.
		//if(abortExposureCommand.isReturnStatusSuccess() == false)
		//{
		//	throw new Exception(this.getClass().getName()+
		//			    ":sendAbortExposureCommand:Abort failed with status: "+
		//			    abortExposureCommand.getReturnStatus()+
		//			    " and message:"+abortExposureCommand.getMessage()+".");
		//}
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendAbortExposureCommand:Finished.");
	}
}
		
