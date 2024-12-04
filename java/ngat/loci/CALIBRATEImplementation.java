// CALIBRATEImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.net.*;
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
 * @see ngat.loci.HardwareImplementation
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
	 * <ul>
	 * <li>We call getCCDFlaskConnectionData to setup ccdFlaskHostname and ccdFlaskPortNumber.
	 * <li>We generate a new BIAS FITS filename to store the Bias data into. We assume the multrun number has
	 *     already been incremented elsewhere.
	 * <li>We setup and configure an instance of TakeBiasFrameCommand, 
	 *     with connection details and the generated filename.
	 * <li>We run the instance of TakeBiasFrameCommand.
	 * <li>We check whether a run exception occured, and throw it as an exception if so.
	 * <li>We log the return status and message.
	 * <li>We check whether the TakeBiasFrameCommand return status was Success, and throw an exception if it
	 *     returned a failure.
	 * <li>We return the generated bias filename.
	 * </ul>
	 * @return The generated BIAS FITS filename is returned as a String.
	 * @see #lociFitsFilename
	 * @see #getCCDFlaskConnectionData
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.TakeBiasFrameCommand
	 * @exception UnknownHostException Thrown if the address passed to TakeBiasFrameCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the TakeBiasFrameCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected String sendTakeBiasFrameCommand() throws UnknownHostException, Exception
	{
		TakeBiasFrameCommand takeBiasFrameCommand = null;
		String filename = null;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeBiasFrameCommand:started.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// setup filename to save bias frame into
		//lociFitsFilename.setExposureCode(FitsFilename.EXPOSURE_CODE_BIAS);
		//lociFitsFilename.nextRunNumber();
		//filename = lociFitsFilename.getFilename();
		//loci.log(Logging.VERBOSITY_INTERMEDIATE,
		//	 "sendTakeBiasFrameCommand:Saving Bias frame to filename:"+filename);
		// setup TakeBiasFrameCommand
		takeBiasFrameCommand = new TakeBiasFrameCommand();
		takeBiasFrameCommand.setAddress(ccdFlaskHostname);
		takeBiasFrameCommand.setPortNumber(ccdFlaskPortNumber);
		//takeBiasFrameCommand.setFilename(filename);
		// run command
		takeBiasFrameCommand.run();
		// check reply
		if(takeBiasFrameCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeBiasFrameCommand:Failed to take bias frame:",
					    takeBiasFrameCommand.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendTakeBiasFrameCommand:Take Bias Frame Command Finished with status: "+
			 takeBiasFrameCommand.getReturnStatus()+
			 " and filename:"+takeBiasFrameCommand.getFilename()+
			 " and message:"+takeBiasFrameCommand.getMessage()+".");
		if(takeBiasFrameCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeBiasFrameCommand:Take Bias Frame Command failed with status: "+
					    takeBiasFrameCommand.getReturnStatus()+
					    " and filename:"+takeBiasFrameCommand.getFilename()+
					    " and message:"+takeBiasFrameCommand.getMessage()+".");
		}
		filename = takeBiasFrameCommand.getFilename();
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeBiasFrameCommand:finished with filename:"+filename);
		return filename;
	}

	/**
	 * Send a 'takeDarkFrame' command to the loci-ctrl CCD Flask API.
	 * <ul>
	 * <li>We call getCCDFlaskConnectionData to setup ccdFlaskHostname and ccdFlaskPortNumber.
	 * <li>We generate a new DARK FITS filename to store the Bias data into. We assume the multrun number has
	 *     already been incremented elsewhere.
	 * <li>We setup and configure an instance of TakeDarkFrameCommand, 
	 *     with connection details, exposure length and the generated filename.
	 * <li>We run the instance of TakeDarkFrameCommand.
	 * <li>We check whether a run exception occured, and throw it as an exception if so.
	 * <li>We log the return status and message.
	 * <li>We check whether the TakeDarkFrameCommand return status was Success, and throw an exception if it
	 *     returned a failure.
	 * <li>We return the generated dark filename.
	 * </ul>
	 * @param exposureLength The dark exposure length in milliseconds.
	 * @return The generated DARK FITS filename is returned as a String.
	 * @see #lociFitsFilename
	 * @see #getCCDFlaskConnectionData
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.TakeDarkFrameCommand
	 * @exception UnknownHostException Thrown if the address passed to TakeDarkFrameCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the TakeDarkFrameCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected String sendTakeDarkFrameCommand(int exposureLength) throws UnknownHostException, Exception
	{
		TakeDarkFrameCommand takeDarkFrameCommand = null;
		String filename = null;
		double exposureLengthS;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeDarkFrameCommand:started with exposure length "+
			 exposureLength+" ms.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// setup filename to save bias frame into
		//lociFitsFilename.setExposureCode(FitsFilename.EXPOSURE_CODE_DARK);
		//lociFitsFilename.nextRunNumber();
		//filename = lociFitsFilename.getFilename();
		//loci.log(Logging.VERBOSITY_INTERMEDIATE,
		//	 "sendTakeDarkFrameCommand:Saving Dark frame to filename:"+filename);
		// convert exposure length from milliseconds to decimal seconds
		exposureLengthS = ((double)exposureLength)/((double)MILLISECONDS_PER_SECOND);
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeDarkFrameCommand:Exposure length "+
			 exposureLengthS+" seconds.");
		// setup TakeDarkFrameCommand
		takeDarkFrameCommand = new TakeDarkFrameCommand();
		takeDarkFrameCommand.setAddress(ccdFlaskHostname);
		takeDarkFrameCommand.setPortNumber(ccdFlaskPortNumber);
		takeDarkFrameCommand.setExposureLength(exposureLengthS);
		//takeDarkFrameCommand.setFilename(filename);
		// run command
		takeDarkFrameCommand.run();
		// check reply
		if(takeDarkFrameCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeDarkFrameCommand:Failed to take dark frame:",
					    takeDarkFrameCommand.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendTakeDarkFrameCommand:Take Dark Frame Command Finished with status: "+
			 takeDarkFrameCommand.getReturnStatus()+
			 " and filename:"+takeDarkFrameCommand.getFilename()+
			 " and message:"+takeDarkFrameCommand.getMessage()+".");
		if(takeDarkFrameCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeDarkFrameCommand:Take Dark Frame Command failed with status: "+
					    takeDarkFrameCommand.getReturnStatus()+
					    " and filename:"+takeDarkFrameCommand.getFilename()+
					    " and message:"+takeDarkFrameCommand.getMessage()+".");
		}
		filename = takeDarkFrameCommand.getFilename();
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeDarkFrameCommand:finished with filename:"+filename);
		return filename;
	}
}
