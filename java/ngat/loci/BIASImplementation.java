// BIASImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the BIAS command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: BIASImplementation.java $
 * @see ngat.loci.CALIBRATEImplementation
 */
public class BIASImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public BIASImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.BIAS&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.BIAS";
	}

	/**
	 * This method returns the BIAS command's acknowledge time. We get the max readout time 
	 * and add the default acknowledge time (getDefaultAcknowledgeTime).
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociStatus#getMaxReadoutTime
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 * @see CALIBRATEImplementation#MILLISECONDS_PER_SECOND
	 * @see #status
	 * @see #serverConnectionThread
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;
		int ackTime=0;

		ackTime = status.getMaxReadoutTime();
		loci.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			 ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime+serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the BIAS command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to loci-crtl
	 *     CCD Flask layer.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). 
	 *     These are sent on to the loci-crtl CCD Flask layer.
	 * <li>We send a takeBiasFrame command to the loci-crtl CCD Flask layer, which returns the generated
	 *     Bias image filename.
	 * <li>The done object is setup, and the generated filename returned. 
	 * </ul>
	 * @see #testAbort
	 * @see ngat.loci.LociStatus#setExposureCount
	 * @see ngat.loci.LociStatus#setExposureNumber
	 * @see ngat.loci.CALIBRATEImplementation#sendTakeBiasFrameCommand
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFitsHeaders
	 * @see ngat.loci.HardwareImplementation#getFitsHeadersFromISS
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		BIAS biasCommand = (BIAS)command;
		BIAS_DONE biasDone = new BIAS_DONE(command.getId());
		String filename = null;
		
		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(biasCommand,biasDone) == true)
			return biasDone;
	// setup exposure status.
		status.setExposureCount(1);
		status.setExposureNumber(0);
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			biasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+701);
			biasDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			biasDone.setSuccessful(false);
			return biasDone;
		}
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(biasCommand,biasDone) == false)
			return biasDone;
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:Setting per-frame FITS headers.");
		if(setPerFrameFitsHeaders(biasCommand,biasDone,FitsHeaderDefaults.OBSTYPE_VALUE_BIAS,0,1,1) == false)
				return biasDone;
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(biasCommand,biasDone) == false)
			return biasDone;
		if(testAbort(biasCommand,biasDone) == true)
			return biasDone;
		// setup bias multrun
		loci.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			 ":processCommand:Setting up FITS filename multrun.");
		// call take bias frame command
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:Starting sendTakeBiasFrameCommand(isMultrunStart=true).");
		try
		{
			filename = sendTakeBiasFrameCommand(true);
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:sendTakeBiasFrameCommand failed:",e);
			biasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+700);
			biasDone.setErrorString(this.getClass().getName()+
						   ":processCommand:sendTakeBiasFrameCommand failed:"+e);
			biasDone.setSuccessful(false);
			return biasDone;
		}
		// update status
		status.setExposureNumber(1);
		status.setExposureFilename(filename);
		// setup return values.
		// setup bias done
		biasDone.setFilename(filename);
		// standard success values
		biasDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		biasDone.setErrorString("");
		biasDone.setSuccessful(true);
	// return done object.
		loci.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return biasDone;
	}
}
