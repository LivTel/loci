// EXPOSEImplementation.java
// $Id$
package ngat.loci;

import java.io.IOException;
import java.net.*;
import java.util.List;

import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;
import ngat.util.logging.*;

/**
 * This class provides the generic implementation for EXPOSE commands sent to a server using the
 * Java Message System. It extends HardwareImplementation, as EXPOSE commands needs access to
 * resources to move mechanisms and write FITS images.
 * @see HardwareImplementation
 * @author Chris Mottram
 * @version $Revision$
 */
public class EXPOSEImplementation extends HardwareImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

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
	 * Send a 'takeExposure' command to the loci-ctrl CCD Flask API.
	 * <ul>
	 * <li>We call getCCDFlaskConnectionData to setup ccdFlaskHostname and ccdFlaskPortNumber.
	 * <li>We setup and configure an instance of TakeExposureCommand, 
	 *     with connection details, exposure length, is multrun start and exposure type.
	 * <li>We run the instance of TakeExposureCommand.
	 * <li>We check whether a run exception occured, and throw it as an exception if so.
	 * <li>We log the return status and message.
	 * <li>We check whether the TakeExposureCommand return status was Success, and throw an exception if it
	 *     returned a failure.
	 * <li>We return the generated exposure filename.
	 * </ul>
	 * @param exposureLength The dark exposure length in milliseconds.
	 * @param isMultrunStart A boolean, true if this is the first frame in the multrun, false otherwise.
	 * @param exposureType  A string representing the type of exposure, usually "exposure" for an exposure, and
	 *        "standard" if the exposure is of a standard star.
	 * @return The generated exposure FITS filename is returned as a String.
	 * @see #getCCDFlaskConnectionData
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.TakeExposureCommand
	 * @exception UnknownHostException Thrown if the address passed to TakeExposureCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the TakeExposureCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected String sendTakeExposureCommand(int exposureLength,boolean isMultrunStart,
						 String exposureType) throws UnknownHostException, Exception
	{
		TakeExposureCommand takeExposureCommand = null;
		String filename = null;
		double exposureLengthS;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeExposureCommand:started with exposure length "+
			 exposureLength+" ms.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// convert exposure length from milliseconds to decimal seconds
		exposureLengthS = ((double)exposureLength)/((double)LociConstants.MILLISECONDS_PER_SECOND);
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeExposureCommand:Exposure length: "+
			 exposureLengthS+" seconds, isMultrunStart: "+isMultrunStart+
			 ", exposure type: "+exposureType+".");
		// setup TakeExposureCommand
		takeExposureCommand = new TakeExposureCommand();
		takeExposureCommand.setAddress(ccdFlaskHostname);
		takeExposureCommand.setPortNumber(ccdFlaskPortNumber);
		takeExposureCommand.setExposureLength(exposureLengthS);
		takeExposureCommand.setMultrun(isMultrunStart);
		takeExposureCommand.setExposureType(exposureType);
		// run command
		takeExposureCommand.run();
		// check reply
		if(takeExposureCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeExposureCommand:Failed to take exposure:",
					    takeExposureCommand.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendTakeExposureCommand:Take Exposure Command Finished with status: "+
			 takeExposureCommand.getReturnStatus()+
			 " and filename:"+takeExposureCommand.getFilename()+
			 " and message:"+takeExposureCommand.getMessage()+".");
		if(takeExposureCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendTakeExposureCommand:Take Exposure Command failed with status: "+
					    takeExposureCommand.getReturnStatus()+
					    " and filename:"+takeExposureCommand.getFilename()+
					    " and message:"+takeExposureCommand.getMessage()+".");
		}
		filename = takeExposureCommand.getFilename();
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeExposureCommand:finished with filename:"+filename);
		return filename;
	}	

	/**
	 * This routine calls the Real Time Data Pipeline to process the expose FITS image we have just captured.
	 * If an error occurs the done objects field's are set accordingly. If the operation succeeds, and the
	 * done object is of class EXPOSE_DONE, the done object is filled with data returned from the 
	 * reduction command.
	 * @param command The command being implemented that made this call to the DP(RT). This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @param filename The filename of the FITS image filename to reduce.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see Loci#sendDpRtCommand
	 */
	public boolean reduceExpose(COMMAND command,COMMAND_DONE done,String filename)
	{
		EXPOSE_REDUCE reduce = new EXPOSE_REDUCE(command.getId());
		INST_TO_DP_DONE instToDPDone = null;
		EXPOSE_REDUCE_DONE reduceDone = null;
		EXPOSE_DONE exposeDone = null;

		reduce.setFilename(filename);
		reduce.setWcsFit(false);
		instToDPDone = loci.sendDpRtCommand(reduce,serverConnectionThread);
		if(instToDPDone.getSuccessful() == false)
		{
			loci.error(this.getClass().getName()+":reduce:"+
				    command+":"+instToDPDone.getErrorNum()+":"+instToDPDone.getErrorString());
			done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+600);
			done.setErrorString(instToDPDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
		// Copy the DP REDUCE DONE parameters to the EXPOSE DONE parameters
		if(instToDPDone instanceof EXPOSE_REDUCE_DONE)
		{
			reduceDone = (EXPOSE_REDUCE_DONE)instToDPDone;
			if(done instanceof EXPOSE_DONE)
			{
				exposeDone = (EXPOSE_DONE)done;
				exposeDone.setFilename(reduceDone.getFilename());
				exposeDone.setSeeing(reduceDone.getSeeing());
				exposeDone.setCounts(reduceDone.getCounts());
				exposeDone.setXpix(reduceDone.getXpix());
				exposeDone.setYpix(reduceDone.getYpix());
				exposeDone.setPhotometricity(reduceDone.getPhotometricity());
				exposeDone.setSkyBrightness(reduceDone.getSkyBrightness());
				exposeDone.setSaturation(reduceDone.getSaturation());
			}
		}
		return true;
	}
}

