// CALIBRATEImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.loci.ccd.*;
import ngat.util.logging.*;

/**
 * This class provides common methods to send bias and dark commands to the ccd-ctrl CCD Flask API,
 * as need by several Bias and Dark calibration implementations.
 * @version $Revision: CALIBRATEImplementation.java $
 * @see ngat.liric.HardwareImplementation
 */
public class CALIBRATEImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The number of milliseconds in one second.
	 */
	public final static int MILLISECONDS_PER_SECOND = 1000;

	/**
	 * This method calls the super-classes method. 
	 * @param command The command to be implemented.
	 */
	public void init(COMMAND command)
	{
		super.init(command);
	}
	
	/**
	 * This method is used to calculate how long an implementation of a command is going to take, so that the
	 * client has an idea of how long to wait before it can assume the server has died.
	 * @param command The command to be implemented.
	 * @return The time taken to implement this command, or the time taken before the next acknowledgement
	 * is to be sent.
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		return super.calculateAcknowledgeTime(command);
	}

	/**
	 * This routine performs the generic command implementation.
	 * @param command The command to be implemented.
	 * @return The results of the implementation of this command.
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		return super.processCommand(command);
	}

	/**
	 * Send a 'takeBiasFrame' command to the loci-ctrl CCD Flask API.
	 */
	protected void sendTakeBiasFrameCommand()
	{
		TakeBiasFrameCommand takeBiasFrameCommand = null;

		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeBiasFrameCommand:started.");
		takeBiasFrameCommand = new TakeBiasFrameCommand();
		diddly
		takeBiasFrameCommand.setAddress(
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeBiasFrameCommand:finished.");
		
	}
