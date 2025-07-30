// TWILIGHT_CALIBRATEImplementation.java
// $Id$
package ngat.loci;

import java.io.*;
import java.lang.*;
import java.net.*;
import java.util.*;

import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;

import ngat.message.ISS_INST.OFFSET_FOCUS;
import ngat.message.ISS_INST.OFFSET_FOCUS_DONE;
import ngat.message.ISS_INST.OFFSET_RA_DEC;
import ngat.message.ISS_INST.OFFSET_RA_DEC_DONE;
import ngat.message.ISS_INST.INST_TO_ISS_DONE;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_ACK;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_DP_ACK;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_DONE;
import ngat.util.*;
import ngat.util.logging.*;


/**
 * This class provides the implementation of a TWILIGHT_CALIBRATE command sent to a server using the
 * Java Message System. It performs a series of SKYFLAT frames from a configurable list,
 * taking into account frames done in previous invocations of this command (it saves it's state).
 * The exposure length is dynamically adjusted as the sky gets darker or brighter. TWILIGHT_CALIBRATE commands
 * should be sent to Loci just after sunset and just before sunrise.
 * @author Chris Mottram
 * @version $Revision: 1.5 $
 */
public class TWILIGHT_CALIBRATEImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The number of different binning factors we should min/best/max count data for.
	 * Actually 1 more than the maximum used binning, as we go from 1 not 0.
	 * @see #minMeanCounts
	 * @see #maxMeanCounts
	 * @see #bestMeanCounts
	 */
	protected final static int BIN_COUNT 	     = 5;
	/**
	 * Initial part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_STRING = "loci.twilight_calibrate.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_CALIBRATION_STRING = "calibration.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNSET_STRING = "sunset.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNRISE_STRING = "sunrise.";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_BIN_STRING = ".bin";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_FILTER_STRING = ".filter";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_FREQUENCY_STRING = ".frequency";
	/**
	 * Middle part of a key string, used for saving and restoring the stored calibration state.
	 */
	protected final static String LIST_KEY_LAST_TIME_STRING = "last_time.";
	/**
	 * Middle part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_OFFSET_STRING = "offset.";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_RA_STRING = ".ra";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_DEC_STRING = ".dec";
	/**
	 * Middle part of a key string, used to get comparative filter sensitivity.
	 */
	protected final static String LIST_KEY_FILTER_SENSITIVITY_STRING = "filter_sensitivity.";
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_UNKNOWN = 0;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNSET	= 1;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNRISE = 2;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame did not have enough counts to be useful, i.e. the mean counts were less than minMeanCounts[bin].
	 * @see #minMeanCounts
	 */
	protected final static int FRAME_STATE_UNDEREXPOSED 	= 0;
	/**
	 * A possible state of a frame taken by this command. 
	 * The mean counts for the frame were sensible, i.e. the mean counts were more than minMeanCounts[bin] and less
	 * than maxMeanCounts[bin].
	 * @see #minMeanCounts
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OK 		= 1;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame had too many counts to be useful, i.e. the mean counts were higher than maxMeanCounts[bin].
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OVEREXPOSED 	= 2;
	/**
	 * The number of possible frame states.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 */
	protected final static int FRAME_STATE_COUNT 	= 3;
	/**
	 * Description strings for the frame states, indexed by the frame state enumeration numbers.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 * @see #FRAME_STATE_COUNT
	 */
	protected final static String FRAME_STATE_NAME_LIST[] = {"underexposed","ok","overexposed"};
	/**
	 * The time, in milliseconds since the epoch, that the implementation of this command was started.
	 */
	private long implementationStartTime = 0L;
	/**
	 * The saved state of calibrations done over time by invocations of this command.
	 * @see TWILIGHT_CALIBRATESavedState
	 */
	private TWILIGHT_CALIBRATESavedState twilightCalibrateState = null;
	/**
	 * The filename holding the saved state data.
	 */
	private String stateFilename = null;
	/**
	 * The list of calibrations to select from.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATECalibration.
	 * @see TWILIGHT_CALIBRATECalibration
	 */
	protected List calibrationList = null;
	/**
	 * The list of telescope offsets to do each calibration for.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATEOffset.
	 * @see TWILIGHT_CALIBRATEOffset
	 */
	protected List offsetList = null;
	/**
	 * The frame overhead for a full frame, in milliseconds. This takes into account readout time,
	 * real time data reduction, communication overheads and the like.
	 */
	private int frameOverhead = 0;
	/**
	 * The minimum allowable exposure time for a frame, in milliseconds.
	 */
	private int minExposureLength = 0;
	/**
	 * The maximum allowable exposure time for a frame, in milliseconds.
	 */
	private int maxExposureLength = 0;
	/**
	 * The exposure time for the current frame, in milliseconds.
	 */
	private int exposureLength = 0;
	/**
	 * The exposure time for the last frame exposed, in milliseconds.
	 */
	private int lastExposureLength = 0;
	/**
	 * What time of night we are doing the calibration, is it sunset or sunrise.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #TIME_OF_NIGHT_SUNRISE
	 */
	private int timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	/**
	 * Filename used to save FITS frames to, until they are determined to contain valid data
	 * (the counts in them are within limits).
	 */
	private String temporaryFITSFilename = null;
	/**
	 * The last mean counts measured by the DpRt on the last frame exposed.
	 * Probably associated with the exposure length lastExposureLength most of the time.
	 */
	private float meanCounts;
	/**
	 * The minimum mean counts. A &quot;good&quot; frame will have a mean counts greater than this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int minMeanCounts[] = {0,0,0,0,0};
	/**
	 * The best mean counts. The &quot;ideal&quot; frame will have a mean counts of this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int bestMeanCounts[] = {0,0,0,0,0};
	/**
	 * The maximum mean counts. A &quot;good&quot; frame will have a mean counts less than this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int maxMeanCounts[] = {0,0,0,0,0};
	/**
	 * The last relative filter sensitivity used for calculating exposure lengths.
	 */
	private double lastFilterSensitivity = 1.0;
	/**
	 * The last bin factor used for calculating exposure lengths.
	 */
	private int lastBin = 1;
	/**
	 * Loop terminator for the calibration list loop.
	 */
	private boolean doneCalibration = false;
	/**
	 * Loop terminator for the offset list loop.
	 */
	private boolean doneOffset = false;
	/**
	 * The number of calibrations completed that have a frame state of good,
	 * for the currently executing calibration.
	 * A calibration is successfully completed if the calibrationFrameCount is
	 * equal to the offsetList size at the end of the offset list loop.
	 */
	private int calibrationFrameCount = 0;
	private int exposureIndex = 0;
	
	/**
	 * Constructor.
	 */
	public TWILIGHT_CALIBRATEImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.TWILIGHT_CALIBRATE&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.TWILIGHT_CALIBRATE";
	}

	/**
	 * This method is the first to be called in this class. 
	 * <ul>
	 * <li>It calls the superclass's init method.
	 * </ul>
	 * @param command The command to be implemented.
	 */
	public void init(COMMAND command)
	{
		super.init(command);
	}

	/**
	 * This method gets the TWILIGHT_CALIBRATE command's acknowledge time. 
	 * This returns the server connection threads default acknowledge time.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see OTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the TWILIGHT_CALIBRATE command.
	 * <ul>
	 * <li>The implementation start time is saved.
	 * <li><b>setTimeOfNight</b> is called to set the time of night flag.
	 * <li><b>loadProperties</b> is called to get configuration data from the properties.
	 * <li><b>addSavedStateToCalibration</b> is called, which finds the correct last time for each
	 * 	calibration in the list and sets the relevant field.
	 * <li>The FITS headers are cleared, and a the MULTRUN number is incremented.
	 * <li>The fold mirror is moved to the correct location using <b>moveFold</b>.
	 * <li>For each calibration, we do the following:
	 *      <ul>
	 *      <li><b>doCalibration</b> is called.
	 *      </ul>
	 * <li>sendBasicAck is called, to stop the client timing out whilst creating the master flat.
	 * <li>The makeMasterFlat method is called, to create master flat fields from the data just taken.
	 * </ul>
	 * Note this method assumes the loading and initialisation before the main loop takes less than the
	 * default acknowledge time, as no ACK's are sent to the client until we are ready to do the first
	 * sequence of calibration frames.
	 * @param command The command to be implemented.
	 * @return An instance of TWILIGHT_CALIBRATE_DONE is returned, with it's fields indicating
	 * 	the result of the command implementation.
	 * @see #implementationStartTime
	 * @see #exposureLength
	 * @see #lastFilterSensitivity
	 * @see #setTimeOfNight
	 * @see #addSavedStateToCalibration
	 * @see #doCalibration
	 * @see #frameOverhead
	 * @see #exposureIndex
	 * @see ngat.loci.HardwareImplementation#moveFold
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.lociCALIBRATEImplementation#makeMasterFlat
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		TWILIGHT_CALIBRATE twilightCalibrateCommand = (TWILIGHT_CALIBRATE)command;
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone = new TWILIGHT_CALIBRATE_DONE(command.getId());
		TWILIGHT_CALIBRATECalibration calibration = null;
		int calibrationListIndex = 0;
		String directoryString = null;
		int makeFlatAckTime;

		twilightCalibrateDone.setMeanCounts(0.0f);
		twilightCalibrateDone.setPeakCounts(0.0f);
	// initialise
		implementationStartTime = System.currentTimeMillis();
		setTimeOfNight();
		if(loadProperties(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// initialise status/fits header info, in case any frames are produced.
		status.setExposureCount(-1);
		status.setExposureNumber(0);
		exposureIndex = 0;
	// match saved state to calibration list (put last time into calibration list)
		if(addSavedStateToCalibration(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// move the fold mirror to the correct location
		if(moveFold(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
		if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
			return twilightCalibrateDone;
	// initialise exposureLength
		if(timeOfNight == TIME_OF_NIGHT_SUNRISE)
			exposureLength = maxExposureLength;
		else if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			exposureLength = minExposureLength;
		else // this should never happen
			exposureLength = minExposureLength;
		// set lastFilterSensitivity/lastBin to contents of first calibration so initial exposure length
		// remains the same as the calculated above.
		lastExposureLength = exposureLength;
		lastFilterSensitivity = 1.0;
		lastBin = 1;
		if(calibrationList.size() > 0)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(0));
			lastFilterSensitivity = calibration.getFilterSensitivity();
			lastBin = calibration.getBin();
		}
		// initialise meanCounts
		meanCounts = bestMeanCounts[lastBin];
		// initialise loop variables
		calibrationListIndex = 0;
		doneCalibration = false;
	// main loop, do calibrations until we run out of time.
		while((doneCalibration == false) && (calibrationListIndex < calibrationList.size()))
		{
		// get calibration
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(calibrationListIndex));
		// do calibration
			if(doCalibration(twilightCalibrateCommand,twilightCalibrateDone,calibration) == false)
				return twilightCalibrateDone;
			calibrationListIndex++;
		}// end for on calibration list
	// send an ack before make master processing, so the client doesn't time out.
		makeFlatAckTime = status.getPropertyInteger("loci.twilight_calibrate.acknowledge_time.make_flat");
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,makeFlatAckTime) == false)
			return twilightCalibrateDone;
	// Call pipeline to create master flat.
		directoryString = status.getProperty("loci.file.fits.path");
		if(directoryString.endsWith(System.getProperty("file.separator")) == false)
			directoryString = directoryString.concat(System.getProperty("file.separator"));
		if(makeMasterFlat(twilightCalibrateCommand,twilightCalibrateDone,directoryString) == false)
			return twilightCalibrateDone;
	// return done
		twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		twilightCalibrateDone.setErrorString("");
		twilightCalibrateDone.setSuccessful(true);
		return twilightCalibrateDone;
	}

	/**
	 * Method to set time of night flag.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNRISE
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #timeOfNight
	 */
	protected void setTimeOfNight()
	{
		Calendar calendar = null;
		int hour;

		timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	// get Instance initialises the calendar to the current time.
		calendar = Calendar.getInstance();
	// the hour returned using HOUR of DAY is between 0 and 23
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		if(hour < 12)
			timeOfNight = TIME_OF_NIGHT_SUNRISE;
		else
			timeOfNight = TIME_OF_NIGHT_SUNSET;
	}

	/**
	 * Method to load twilight calibration configuration data from the LOCI Properties file.
	 * The following configuration properties are retrieved:
	 * <ul>
	 * <li>frame overhead
	 * <li>minimum exposure length
	 * <li>maximum exposure length
	 * <li>temporary FITS filename
	 * <li>saved state filename
	 * <li>minimum mean counts (for each binning factor)
	 * <li>best mean counts (for each binning factor)
	 * <li>maximum mean counts (for each binning factor)
	 * </ul>
	 * The following methods are then called to load more calibration data:
	 * <ul>
	 * <li><b>loadCalibrationList</b>
	 * <li><b>loadOffsetList</b>
	 * <li><b>loadState</b>
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if the method succeeds, false if an error occurs.
	 * 	If false is returned the error data in twilightCalibrateDone is filled in.
	 * @see #loadCalibrationList
	 * @see #loadState
	 * @see #loadOffsetList
	 * @see #frameOverhead
	 * @see #minExposureLength
	 * @see #maxExposureLength
	 * @see #temporaryFITSFilename
	 * @see #stateFilename
	 * @see #minMeanCounts
	 * @see #bestMeanCounts
	 * @see #maxMeanCounts
	 * @see #timeOfNight
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #LIST_KEY_STRING
	 */
	protected boolean loadProperties(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		String timeOfNightString = null;
		String propertyName = null;

		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		try
		{
		// frame overhead
			propertyName = LIST_KEY_STRING+"frame_overhead";
			frameOverhead = status.getPropertyInteger(propertyName);
		// minimum exposure length
			propertyName = LIST_KEY_STRING+"min_exposure_time";
			minExposureLength = status.getPropertyInteger(propertyName);
		// maximum exposure length
			propertyName = LIST_KEY_STRING+"max_exposure_time";
			maxExposureLength = status.getPropertyInteger(propertyName);
		// temporary FITS filename
		// This configuration cannot be used at the moment due to limitations in the loci camera API / filename API
			propertyName = LIST_KEY_STRING+"file.tmp";
			temporaryFITSFilename = status.getProperty(propertyName);
		// saved state filename
			propertyName = LIST_KEY_STRING+"state_filename";
			stateFilename = status.getProperty(propertyName);
			// binning goes from 1..BIN_COUNT-1
			for(int binIndex = 1; binIndex < BIN_COUNT; binIndex ++)
			{
				// minimum mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.min."+binIndex;
				minMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
				// best mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.best."+binIndex;
				bestMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
				// maximum mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.max."+binIndex;
				maxMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
			}// end for on binIndex
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadProperties:Failed to get property:"+propertyName);
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2300);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		if(loadCalibrationList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadOffsetList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadState(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		return true;
	}

	/**
	 * Method to load a list of calibrations to do. The list used depends on whether timeOfNight is set to
	 * sunrise or sunset.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #calibrationList
	 * @see #getFilterSensitivity
	 * @see #LIST_KEY_STRING
	 * @see #LIST_KEY_CALIBRATION_STRING
	 * @see #LIST_KEY_FILTER_STRING
	 * @see #LIST_KEY_BIN_STRING
	 * @see #LIST_KEY_FREQUENCY_STRING
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #timeOfNight
	 */
	protected boolean loadCalibrationList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		String timeOfNightString = null;
		String filter = null;
		int index,bin;
		long frequency;
		double filterSensitivity;
		boolean done;

		index = 0;
		done = false;
		calibrationList = new Vector();
		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		while(done == false)
		{
			filter = status.getProperty(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_FILTER_STRING);
			if(filter != null)
			{
			// create calibration instance
				calibration = new TWILIGHT_CALIBRATECalibration();
			// get parameters from properties
				try
				{
					frequency = status.getPropertyLong(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_FREQUENCY_STRING);
					bin = status.getPropertyInteger(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_BIN_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed at index "+index+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2301);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set calibration data
				try
				{
					calibration.setBin(bin);
					calibration.setFilter(filter);
					calibration.setFrequency(frequency);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed to set calibration data at index "+index+
						":bin:"+bin+":filter:"+filter+":frequency:"+frequency+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2302);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// get filter sensitivity, and set calibration sensitivities
				try
				{
					filterSensitivity = getFilterSensitivity(filter);
					calibration.setFilterSensitivity(filterSensitivity);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed to set filter sensitivities at index "+
						index+":bin:"+bin+
						":filter:"+filter+":frequency:"+frequency+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2303);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add calibration instance to list
				calibrationList.add(calibration);
			// log
				loci.log(Logging.VERBOSITY_VERBOSE,
					"Command:"+twilightCalibrateCommand.getClass().getName()+
					":Loaded calibration:"+index+
					":bin:"+calibration.getBin()+
					":filter:"+calibration.getFilter()+
					":frequency:"+calibration.getFrequency()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * Method to get the relative filter sensitivity of a filter.
	 * @param filterType The type name of the filter to find the sensitivity for.
	 * @return A double is returned, which is the filter sensitivity relative to no filter,
	 * 	and should be in the range 0 to 1.
	 * @exception IllegalArgumentException Thrown if the filter sensitivity returned from the
	 * 	property is out of the range 0..1.
	 * @exception NumberFormatException  Thrown if the filter sensitivity property is not a valid double.
	 */
	protected double getFilterSensitivity(String filterType) throws NumberFormatException, IllegalArgumentException
	{
		double filterSensitivity;

		filterSensitivity = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_FILTER_SENSITIVITY_STRING+
								filterType);
		if((filterSensitivity < 0.0)||(filterSensitivity > 1.0))
		{
			throw new IllegalArgumentException(this.getClass().getName()+
				":getFilterSensitivity failed:filter type "+filterType+
				" has filter sensitivity "+filterSensitivity+", which is out of range.");
		}
		return filterSensitivity;
	}

	/**
	 * Method to initialse twilightCalibrateState.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #twilightCalibrateState
	 * @see #stateFilename
	 */
	protected boolean loadState(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				    TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
	// initialise and load twilightCalibrateState instance
		twilightCalibrateState = new TWILIGHT_CALIBRATESavedState();
		try
		{
			twilightCalibrateState.load(stateFilename);
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadState:Failed to load state filename:"+stateFilename);
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2304);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to load a list of telescope RA/DEC offsets. These are used to offset the telescope
	 * between frames of the same calibration.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #offsetList
	 * @see #LIST_KEY_OFFSET_STRING
	 * @see #LIST_KEY_RA_STRING
	 * @see #LIST_KEY_DEC_STRING
	 */
	protected boolean loadOffsetList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
					 TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{

		TWILIGHT_CALIBRATEOffset offset = null;
		String testString = null;
		int index;
		double raOffset,decOffset;
		boolean done;

		index = 0;
		done = false;
		offsetList = new Vector();
		while(done == false)
		{
			testString = status.getProperty(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
			if((testString != null))
			{
			// create offset
				offset = new TWILIGHT_CALIBRATEOffset();
			// get parameters from properties
				try
				{
					raOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
					decOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_DEC_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed at index "+index+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2305);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set offset data
				try
				{
					offset.setRAOffset((float)raOffset);
					offset.setDECOffset((float)decOffset);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed to set data at index "+index+
						":RA offset:"+raOffset+":DEC offset:"+decOffset+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2306);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add offset instance to list
				offsetList.add(offset);
			// log
				loci.log(Logging.VERBOSITY_VERBOSE,
					 "Command:"+twilightCalibrateCommand.getClass().getName()+
					 ":Loaded offset "+index+
					 ":RA Offset:"+offset.getRAOffset()+
					 ":DEC Offset:"+offset.getDECOffset()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * This method matches the saved state to the calibration list to set the last time
	 * each calibration was completed.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. Currently always returns true.
	 * @see #calibrationList
	 * @see #twilightCalibrateState
	 */
	protected boolean addSavedStateToCalibration(TWILIGHT_CALIBRATE twilightCalibrateCommand,
						     TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		String filter = null;
		int bin;
		long lastTime;

		for(int i = 0; i< calibrationList.size(); i++)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(i));
			bin = calibration.getBin();
			filter = calibration.getFilter();
			lastTime = twilightCalibrateState.getLastTime(bin,filter);
			calibration.setLastTime(lastTime);
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+":Calibration:"+
				 "bin:"+calibration.getBin()+
				 ":filter:"+calibration.getFilter()+
				 ":frequency:"+calibration.getFrequency()+
				 " now has last time set to:"+lastTime+".");
		}
		return true;
	}

	/**
	 * This method does the specified calibration.
	 * <ul>
	 * <li>The relevant data is retrieved from the calibration parameter.
	 * <li>If we did this calibration more recently than frequency, log and return.
	 * <li>An optimal exposure length is calculated, by dividing by the last relative sensitivity used
	 * 	(to get the exposure length as if though a clear filter), and then dividing by the 
	 * 	new relative filter sensitivity (to increase the exposure length).
	 * <li>The optimal exposure length is recalculated to take account of differences from the last binning
	 *     to the new binning.
	 * <li>We set the exposure length to be a range bound version of optimal exposure length, between
	 *     the minimum and maximum exposure length.
	 * <li>We calculate the predicted mean counts, by taking the last mean counts and adjusting by the ratios
	 *     between the old and new filter sensitivity, the old and new binning squared (as binning 2 allows
	 *     four times the flux to fall on a pixel as binning 1), and the old and new exposure length.
	 * <li>Check whether we expect the predicted mean counts to be too small at sunset
	 *     (it is too dark for this filter/bin combo). If so, try the next calibration.
	 * <li>Check whether we expect the predicted mean counts to be too big at sunrise
	 *     (it is too light for this filter/bin combo). If so, try the next calibration.
	 * <li>We update the  last filter sensitivity and last binning factor.
	 * <li><b>sendBasicAck</b> is called to stop the client timing out before the config is completed.
	 * <li><b>doConfig</b> is called for the relevant binning factor/slides/filter set to be setup.
	 * <li><b>sendBasicAck</b> is called to stop the client timing out before the first frame is completed.
	 * <li><b>doOffsetList</b> is called to go through the telescope RA/DEC offsets and take frames at
	 * 	each offset.
	 * <li>If the calibration suceeded, the saved state's last time is updated to now, and the state saved.
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param calibration The calibration to do.
	 * @return The method returns true if the calibration was done successfully, false if an error occured.
	 * @see #doConfig
	 * @see #doOffsetList
	 * @see #sendBasicAck
	 * @see #stateFilename
	 * @see #lastFilterSensitivity
	 * @see #lastBin
	 * @see #calibrationFrameCount
	 * @see #meanCounts
	 */
	protected boolean doCalibration(TWILIGHT_CALIBRATE twilightCalibrateCommand,
			TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,TWILIGHT_CALIBRATECalibration calibration)
	{
		String filter = null;
		int bin,optimalExposureLength;
		long lastTime,frequency;
		long now;
		float predictedMeanCounts;
		double filterSensitivity;

		loci.log(Logging.VERBOSITY_VERBOSE,
			 "Command:"+twilightCalibrateCommand.getClass().getName()+
			 ":doCalibrate:"+"bin:"+calibration.getBin()+
			 ":filter:"+calibration.getFilter()+
			 ":frequency:"+calibration.getFrequency()+
			 ":filter sensitivity:"+calibration.getFilterSensitivity()+
			 ":last time:"+calibration.getLastTime()+" Started.");
	// get copy of calibration data
		bin = calibration.getBin();
		filter = calibration.getFilter();
		frequency = calibration.getFrequency();
		lastTime = calibration.getLastTime();
		filterSensitivity = calibration.getFilterSensitivity();
	// get current time
		now = System.currentTimeMillis();
	// if we did the calibration more recently than frequency, log and return
		if(now-lastTime < frequency)
		{
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:"+"bin:"+calibration.getBin()+
				 ":filter:"+calibration.getFilter()+
				 ":frequency:"+calibration.getFrequency()+
				 ":last time:"+lastTime+
				 "NOT DONE: too soon since last completed:"+now+" - "+lastTime+" < "+frequency+".");
			return true;
		}
	// recalculate the exposure length
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "Command:"+twilightCalibrateCommand.getClass().getName()+
			 ":doCalibrate:lastExposureLength:"+lastExposureLength);
		optimalExposureLength = (int)((((double)lastExposureLength)*lastFilterSensitivity)/filterSensitivity);
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "Command:"+twilightCalibrateCommand.getClass().getName()+
			 ":doCalibrate:optimalExposureLength after multiplication through by last filter sensitivity:"+
			 lastFilterSensitivity+"/ filter senisitivity:"+filterSensitivity+" =:"+optimalExposureLength);
		optimalExposureLength = (optimalExposureLength*(lastBin*lastBin))/(bin*bin);
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "Command:"+twilightCalibrateCommand.getClass().getName()+
			 ":doCalibrate:optimalExposureLength after multiplication through by last bin:"+
			 lastBin+" (squared) / bin:"+bin+" (squared) =:"+optimalExposureLength);
		exposureLength = optimalExposureLength;
		if(optimalExposureLength < minExposureLength)
			exposureLength = minExposureLength;
		if(optimalExposureLength > maxExposureLength)
			exposureLength = maxExposureLength;
		predictedMeanCounts = meanCounts * (float)((filterSensitivity/lastFilterSensitivity) * 
				     ((bin*bin)/(lastBin*lastBin))*(exposureLength/lastExposureLength));
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "Command:"+twilightCalibrateCommand.getClass().getName()+
			 ":doCalibrate:predictedMeanCounts are "+
			 predictedMeanCounts+" using exposure legnth "+exposureLength);
		// check whether we expect the predicted mean counts to be too small at sunset
		// (it is too dark for this filter/bin combo). If so, try the next calibration.
		if((timeOfNight == TIME_OF_NIGHT_SUNSET)&&(optimalExposureLength > maxExposureLength)&&
		   (predictedMeanCounts < minMeanCounts[bin]))
		{
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:predictedMeanCounts "+predictedMeanCounts+" too small: Not attempting "+
				 filter+" bin "+bin+".");
			return true; // try next calibration
		}
		// check whether we expect the predicted mean counts to be too big at sunrise
		// (it is too light for this filter/bin combo). If so, try the next calibration.
		if((timeOfNight == TIME_OF_NIGHT_SUNRISE)&&(optimalExposureLength < minExposureLength)&&
		   (predictedMeanCounts > maxMeanCounts[bin]))
		{
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:predictedMeanCounts "+predictedMeanCounts+" too large: Not attempting "+
				 filter+" bin "+bin+".");
			return true; // try next calibration
		}
	// if we are going to do this calibration, reset the last filter sensitivity for next time
	// We need to think about when to do this when the new exposure length means we DON'T do the calibration
		lastFilterSensitivity = filterSensitivity;
		lastBin = bin;
		if((now+exposureLength+frameOverhead) > 
			(implementationStartTime+twilightCalibrateCommand.getTimeToComplete()))
		{
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:Ran out of time to complete:"+
				 "((now:"+now+
				 ")+(exposureLength:"+exposureLength+
				 ")+(frameOverhead:"+frameOverhead+")) > "+
				 "((implementationStartTime:"+implementationStartTime+
				 ")+(timeToComplete:"+twilightCalibrateCommand.getTimeToComplete()+")).");
			return true;
		}
	// send an ack before the frame, so the client doesn't time out during configuration
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,frameOverhead) == false)
			return false;
	// configure filter/CCD camera binning
		if(doConfig(twilightCalibrateCommand,twilightCalibrateDone,bin,filter) == false)
			return false;
	// send an ack before the frame, so the client doesn't time out during the first exposure
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,exposureLength+frameOverhead) == false)
			return false;
	// do the frames with this configuration
		calibrationFrameCount = 0;
		if(doOffsetList(twilightCalibrateCommand,twilightCalibrateDone,bin,filter) == false)
			return false;
	// update state, if we completed the whole calibration.
		if(calibrationFrameCount == offsetList.size())
		{
			twilightCalibrateState.setLastTime(bin,filter);
			try
			{
				twilightCalibrateState.save(stateFilename);
			}
			catch(IOException e)
			{
				String errorString = new String(twilightCalibrateCommand.getId()+
					":doCalibration:Failed to save state filename:"+stateFilename);
				loci.error(this.getClass().getName()+":"+errorString,e);
				twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2307);
				twilightCalibrateDone.setErrorString(errorString);
				twilightCalibrateDone.setSuccessful(false);
				return false;
			}
			lastTime = twilightCalibrateState.getLastTime(bin,filter);
			calibration.setLastTime(lastTime);
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:Calibration successfully completed:"+
				 "bin:"+calibration.getBin()+
				 ":filter:"+calibration.getFilter()+".");
		}// end if done calibration
		else
		{
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+
				 ":doCalibrate:Calibration NOT completed:"+
				 "bin:"+calibration.getBin()+
				 ":filter:"+calibration.getFilter()+".");
		}
		return true;
	}

	/**
	 * Method to setup the CCD configuration with the specified binning factor, and specified filter.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param bin The binning factor to use.
	 * @param filter The type of filter to use.
	 * @return The method returns true if the calibration was done successfully, false if an error occured.
	 * @see #loci
	 * @see #sendSetImageDimensionsCommand
	 * @see ngat.loci.Loci#getStatus
	 * @see ngat.loci.Loci#error
	 * @see ngat.loci.Loci#log
	 * @see ngat.loci.LociStatus#incConfigId
	 * @see ngat.loci.LociConstants#LOCI_ERROR_CODE_BASE
	 * @see ngat.loci.LociConstants#LOCI_ERROR_CODE_NO_ERROR
	 * @see ngat.loci.CommandImplementation#testAbort
	 * @see ngat.loci.HardwareImplementation#sendSetFilterPositionByNameCommand
	 * @see ngat.loci.HardwareImplementation#setFocusOffset
	 */
	protected boolean doConfig(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				   TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int bin,String filter)
	{
		String filterIdName = null;
		float focusOffset,filterFocusOffset;

		try
		{
			// send configuration to the camera
			sendSetImageDimensionsCommand(bin,bin);
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
			// move filter wheel to specified filter
			sendSetFilterPositionByNameCommand(filter);
		}
		catch(Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":doConfig:Failed to configure CCD/filter wheel:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2309);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
	// test abort
		if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
			return false;
	// Get overall instrument focus offset
		try
		{
			focusOffset = status.getPropertyFloat("loci.focus.offset");
			loci.log(Logging.VERBOSITY_TERSE,"Command:"+
				 twilightCalibrateCommand.getClass().getName()+":instrument focus offset = "+focusOffset+".");
		}
		catch(NumberFormatException e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+twilightCalibrateCommand,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2323);
			twilightCalibrateDone.setErrorString(e.toString());
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
	// Get filter focus offset
		try
		{
			filterIdName = status.getFilterIdName(filter);
			filterFocusOffset = (float)(status.getFilterIdOpticalThickness(filterIdName));
			loci.log(Logging.VERBOSITY_TERSE,"Command:"+
			   twilightCalibrateCommand.getClass().getName()+":filter focus offset = "+filterFocusOffset+".");
			focusOffset += filterFocusOffset;
			loci.log(Logging.VERBOSITY_VERY_TERSE,"Command:"+
			      twilightCalibrateCommand.getClass().getName()+":overall focus offset = "+focusOffset+".");
		}
		catch(NumberFormatException e)
		{
			loci.error(this.getClass().getName()+":processCommand:"+twilightCalibrateCommand,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2324);
			twilightCalibrateDone.setErrorString(e.toString());
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
	// actually issue ISS OFFSET_FOCUS commmand to telescope/ISS. 
		try
		{
			setFocusOffset(twilightCalibrateCommand.getId(),focusOffset,twilightCalibrateDone);
		}
		catch(Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
							"doConfig:setFocusOffset failed:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2322);
			twilightCalibrateDone.setErrorString(errorString+e);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
	// Increment unique config ID.
	// This is queried when saving FITS headers to get the CONFIGID value.
		try
		{
			status.incConfigId();
		}
		catch(Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":doConfig:Incrementing configuration ID failed:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2310);
			twilightCalibrateDone.setErrorString(errorString+e);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
	// Store name of configuration used in status object.
	// This is queried when saving FITS headers to get the CONFNAME value.
		status.setConfigName("TWILIGHT_CALIBRATION:"+twilightCalibrateCommand.getId()+":"+bin+":"+filter);
		return true;
	}

	/**
	 * Send a setImageWindow CCD Flask API call to configure the detector binning.
	 * <ul>
	 * <li>We get the CCD Flask API connection data by calling getCCDFlaskConnectionData.
	 * <li>We construct and initialise a SetImageDimensionsCommand instance.
	 * <li>We set the detector binning factors.
	 * <li>We run the SetImageDimensionsCommand instance.
	 * <li>We check whether the command threw an exception, or returned an error.
	 * </ul>
	 * @param xBin The X binning factor to configure.
	 * @param yBin The Y binning factor to configure.
	 * @see ngat.loci.HardwareImplementation#getCCDFlaskConnectionData
	 * @see ngat.loci.HardwareImplementation#ccdFlaskHostname
	 * @see ngat.loci.HardwareImplementation#ccdFlaskPortNumber
	 * @see ngat.loci.ccd.SetImageDimensionsCommand
	 * @exception UnknownHostException Thrown if the address passed to SetImageDimensionsCommand.setAddress 
	 *            is not a valid host.
	 * @exception Exception Thrown if the SetImageDimensionsCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected void sendSetImageDimensionsCommand(int xBin,int yBin) throws UnknownHostException, Exception
	{
		SetImageDimensionsCommand command = null;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendSetImageDimensionsCommand:started.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// setup command
		command = new SetImageDimensionsCommand();
		command.setAddress(ccdFlaskHostname);
		command.setPortNumber(ccdFlaskPortNumber);
		// binning
		command.setHorizontalBinning(xBin);
		command.setVerticalBinning(yBin);
		// run command
		command.run();
		// check reply
		if(command.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":sendSetImageDimensionsCommand:Failed:"+command.getRunException(),
					    command.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "sendSetImageDimensionsCommand:Set Image Dimensions Command Finished with status: "+
			 command.getReturnStatus()+" and message:"+command.getMessage()+".");
		if(command.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
				    ":sendSetImageDimensionsCommand:Set Image Dimensions Command failed with status: "+
					    command.getReturnStatus()+" and message:"+command.getMessage()+".");
		}
	}

	/**
	 * This method goes through the offset list for the configured calibration. It trys to
	 * get a frame for each offset.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param bin The binning factor we are doing the exposure at, used to select the correct min/best/max counts.
	 * @param filter The type of filter to use. Passed through for logging purposes.
	 * @return The method returns true when the offset list is terminated, false if an error occured.
	 * @see #offsetList
	 * @see #doFrame
	 * @see Loci#sendISSCommand
	 * @see ngat.message.ISS_INST.OFFSET_RA_DEC
	 */
	protected boolean doOffsetList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				       TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int bin,String filter)
	{
		TWILIGHT_CALIBRATEOffset offset = null;
		OFFSET_RA_DEC offsetRaDecCommand = null;
		INST_TO_ISS_DONE instToISSDone = null;
		int offsetListIndex;

		doneOffset = false;
		offsetListIndex = 0;
		while((doneOffset == false) && (offsetListIndex < offsetList.size()))
		{
		// get offset
			offset = (TWILIGHT_CALIBRATEOffset)(offsetList.get(offsetListIndex));
		// log telescope offset
			loci.log(Logging.VERBOSITY_VERBOSE,
			      "Command:"+twilightCalibrateCommand.getClass().getName()+
			      ":Attempting telescope position offset index:"+offsetListIndex+
			      ":RA:"+offset.getRAOffset()+
			      ":DEC:"+offset.getDECOffset()+".");
		// tell telescope of offset RA and DEC
			offsetRaDecCommand = new OFFSET_RA_DEC(twilightCalibrateCommand.getId());
			offsetRaDecCommand.setRaOffset(offset.getRAOffset());
			offsetRaDecCommand.setDecOffset(offset.getDECOffset());
			instToISSDone = loci.sendISSCommand(offsetRaDecCommand,serverConnectionThread);
			if(instToISSDone.getSuccessful() == false)
			{
				String errorString = null;

				errorString = new String("Offset Ra Dec failed:ra = "+offset.getRAOffset()+
					", dec = "+offset.getDECOffset()+":"+instToISSDone.getErrorString());
				loci.error(errorString);
				twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2312);
				twilightCalibrateDone.setErrorString(this.getClass().getName()+
									":doOffsetList:"+errorString);
				twilightCalibrateDone.setSuccessful(false);
				return false;
			}
		// do exposure at this offset
			if(doFrame(twilightCalibrateCommand,twilightCalibrateDone,bin,filter) == false)
				return false;
			offsetListIndex++;
		}// end for on offset list
		return true;
	}

	/**
	 * The method that does a calibration frame with the current configuration. The following is performed 
	 * in a while loop, that is terminated when a good frame has been taken.
	 * <ul>
	 * <li>The FITS headers setup from the current configuration.
	 * <li>Some FITS headers are got from the ISS.
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to the 
	 *     CCD Flask API.
	 * <li>setFilterWheelFitsHeaders is called to get the current filter wheel position, 
	 *     and set some FITS headers based on this.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li>We call setPerFrameFitsHeaders to set the per-frame FITS headers.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). 
	 *          These are sent on to the CCD Flask API.
	 * <li>It performs an exposure by calling sendTakeExposureCommand.
	 * <li>The last exposure length variable is updated.
	 * <li>An instance of TWILIGHT_CALIBRATE_ACK is sent back to the client using <b>sendTwilightCalibrateAck</b>.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li><b>reduceCalibrate</b> is called to pass the frame to the Real Time Data Pipeline for processing.
	 * <li>The frame state is derived from the returned mean counts.
	 * <li>If the frame state was good, the raw frame and DpRt reduced (if different) are renamed into
	 * 	the standard FITS filename using lociFilename, by incrementing the run number.
	 * <li><b>testAbort</b> is called to see if this command implementation has been aborted.
	 * <li>The optimal exposure Length is calculated by multiplying by the 
	 *     ratio of best mean counts over mean counts.
	 * <li>We change the exposure length to be the optimal exposure length, bracketed by the
	 *     minimum and maximum exposure lengths.
	 * <li>We calculate the predicted mean counts for the bracketed exposure length, by multiplying
	 *     the last mean counts by the ratio of new and last exposure lengths.
	 * <li>We check the predicted mean counts for the bracketed exposure length are within the mean counts limits, 
	 *     otherwise we assume the next exposure will return out of range mean counts and move onto the 
	 *     next calibration.
	 * <li>An instance of TWILIGHT_CALIBRATE_DP_ACK is sent back to the client using
	 * 	<b>sendTwilightCalibrateDpAck</b>.
	 * <li>We check to see if the loop should be terminated:
	 * 	<ul>
	 * 	<li>If the frame state is OK, the loop is exited and the method stopped.
	 * 	<li>If the next exposure will take longer than the time remaining, we stop the frame loop,
	 * 		offset loop and calibration loop (i.e. the TWILIGHT_CALIBRATE command is terminated).
	 * 	</ul>
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param bin The binning factor we are doing the exposure at, used to select the correct min/best/max counts.
	 * @param filter The type of filter to use. Passed through for logging purposes.
	 * @return The method returns true if no errors occured, false if an error occured.
	 * @see CommandImplementation#testAbort
	 * @see HardwareImplementation#setFitsHeaders
	 * @see HardwareImplementation#getFitsHeadersFromISS
	 * @see #sendTwilightCalibrateAck
	 * @see #sendTwilightCalibrateDpAck
	 * @see CALIBRATEImplementation#reduceCalibrate
	 * @see #exposureLength
	 * @see #lastExposureLength
	 * @see #minExposureLength
	 * @see #maxExposureLength
	 * @see #frameOverhead
	 * @see #implementationStartTime
	 * @see #meanCounts
	 * @see #FRAME_STATE_OVEREXPOSED
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_NAME_LIST
	 */
	protected boolean doFrame(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				  TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int bin,String filter)
	{
		String filename = null;
		String reducedFilename = null;
		long now;
		int frameState,optimalExposureLength;
		boolean doneFrame;
		float predictedMeanCounts;

		doneFrame = false;
		while(doneFrame == false)
		{
		// setup fits headers
			clearFitsHeaders();
			if(setFitsHeaders(twilightCalibrateCommand,twilightCalibrateDone) == false)
				return false;
			if(setFilterWheelFitsHeaders(twilightCalibrateCommand,twilightCalibrateDone) == false)
				return false;
			if(getFitsHeadersFromISS(twilightCalibrateCommand,twilightCalibrateDone) == false)
				return false;
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
		// log exposure attempt
			loci.log(Logging.VERBOSITY_VERBOSE,"Command:"+twilightCalibrateCommand.getId()+
				 ":doFrame:"+"bin:"+bin+
				 ":filter:"+filter+
				 ":Attempting exposure: length:"+exposureLength+".");
			// setup per-frame FITS headers
			if(setPerFrameFitsHeaders(twilightCalibrateCommand,twilightCalibrateDone,
						  FitsHeaderDefaults.OBSTYPE_VALUE_SKY_FLAT,
						  exposureLength,-1,exposureIndex) == false)
				return false;
		// do exposure
			try
			{
				filename = sendTakeExposureCommand(exposureLength);
				exposureIndex++;
			}
			catch(CCDLibraryNativeException e)
			{
				String errorString = new String(twilightCalibrateCommand.getId()+
					":doFrame:Doing frame of length "+exposureLength+" failed:");
				loci.error(this.getClass().getName()+":"+errorString,e);
				twilightCalibrateDone.setFilename(temporaryFITSFilename);
				twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2313);
				twilightCalibrateDone.setErrorString(errorString+e);
				twilightCalibrateDone.setSuccessful(false);
				return false;
			}
		// set last exposure length
			lastExposureLength = exposureLength;
		// send with filename back to client
		// time to complete is reduction time, we will send another ACK after reduceCalibrate
			if(sendTwilightCalibrateAck(twilightCalibrateCommand,twilightCalibrateDone,frameOverhead,
						    filename) == false)
				return false; 
		// Test abort status.
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
		// Call pipeline to reduce data.
			if(reduceCalibrate(twilightCalibrateCommand,twilightCalibrateDone,
					   filename) == false)
				return false;
		// Test abort status.
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
		// log reduction
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getId()+
				 ":doFrame:"+"bin:"+bin+
				 ":filter:"+filter+
				 ":Exposure reduction:length "+exposureLength+
				 ":filename:"+twilightCalibrateDone.getFilename()+
				 ":mean counts:"+twilightCalibrateDone.getMeanCounts()+
				 ":peak counts:"+twilightCalibrateDone.getPeakCounts()+".");
		// get reduced filename from done
			reducedFilename = twilightCalibrateDone.getFilename();
		// get mean counts and set frame state.
			meanCounts = twilightCalibrateDone.getMeanCounts();
			// range check mean counts, if they are negative we are saturated due to the way
			// the CCD deals with saturation.
			if(meanCounts < 0)
			{
				loci.log(Logging.VERBOSITY_VERBOSE,
					 "Command:"+twilightCalibrateCommand.getId()+
					 ":doFrame:"+"bin:"+bin+
					 ":filter:"+filter+
					 ":Exposure reduction:length "+exposureLength+
					 ":filename:"+twilightCalibrateDone.getFilename()+
					 ":mean counts:"+twilightCalibrateDone.getMeanCounts()+
					 ":Mean counts are negative, exposure is probably saturated, "+
					 "faking mean counts to 65000.");
				meanCounts = 65000;
			}
			if(meanCounts > maxMeanCounts[bin])
				frameState = FRAME_STATE_OVEREXPOSED;
			else if(meanCounts < minMeanCounts[bin])
				frameState = FRAME_STATE_UNDEREXPOSED;
			else
				frameState = FRAME_STATE_OK;
		// log frame state
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getId()+
				 ":doFrame:"+"bin:"+bin+
				 ":filter:"+filter+
				 ":Exposure frame state:length:"+exposureLength+
				 ":mean counts:"+meanCounts+
				 ":peak counts:"+twilightCalibrateDone.getPeakCounts()+
				 ":frame state:"+FRAME_STATE_NAME_LIST[frameState]+".");
		// if the frame was good, rename it
			// diddly
			//if(frameState == FRAME_STATE_OK)
			//{
			// raw frame
				//temporaryFile = new File(temporaryFITSFilename);
			// does the temprary file exist?
				//if(temporaryFile.exists() == false)
				//{
				//	String errorString = new String(twilightCalibrateCommand.getId()+
				//				":File does not exist:"+temporaryFITSFilename);

				//	loci.error(this.getClass().getName()+
				//		":doFrame:"+errorString);
				//	twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2314);
				//	twilightCalibrateDone.setErrorString(errorString);
				//	twilightCalibrateDone.setSuccessful(false);
				//	return false;
				//}
			// get a filename to store frame in
				//oFilename.nextRunNumber();
				//filename = oFilename.getFilename();
				//newFile = new File(filename);
			// rename temporary filename to filename
				//if(temporaryFile.renameTo(newFile) == false)
				//{
				//	String errorString = new String(twilightCalibrateCommand.getId()+
				//		":Failed to rename '"+temporaryFile.toString()+"' to '"+
				//		newFile.toString()+"'.");

				//	loci.error(this.getClass().getName()+":doFrame:"+errorString);
				//	twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2315);
				//	twilightCalibrateDone.setErrorString(errorString);
				//	twilightCalibrateDone.setSuccessful(false);
				//	return false;
				//}
			// log rename
				//loci.log(Logging.VERBOSITY_VERBOSE,
				//      "Command:"+twilightCalibrateCommand.getId()+
				//      ":doFrame:"+"bin:"+bin+
				//      ":filter:"+filter+
				//      ":Exposure raw frame rename:renamed "+temporaryFile+" to "+newFile+".");
			// reset twilight calibrate done's filename to renamed file
			// in case pipelined reduced filename does not exist/cannot be renamed
				//twilightCalibrateDone.setFilename(filename);
			// real time pipelined processed file
				//temporaryFile = new File(reducedFilename);
			// does the temprary file exist? If it doesn't this is not an error,
			// if the DpRt returned the same file it was passed in it will have already been renamed
				//if(temporaryFile.exists())
				//{
				// get a filename to store pipelined processed frame in
				//	try
				//	{
				//		lociFilename.setPipelineProcessing(FitsFilename.
				//						PIPELINE_PROCESSING_FLAG_REAL_TIME);
				//		filename = lociFilename.getFilename();
				//		lociFilename.setPipelineProcessing(FitsFilename.
				//						PIPELINE_PROCESSING_FLAG_NONE);
				//	}
				//	catch(Exception e)
				//	{
				//		String errorString = new String(twilightCalibrateCommand.getId()+
				//					    ":doFrame:setPipelineProcessing failed:");
				//		loci.error(this.getClass().getName()+":"+errorString,e);
				//		twilightCalibrateDone.setFilename(reducedFilename);
				//		twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2321);
				//		twilightCalibrateDone.setErrorString(errorString+e);
				//		twilightCalibrateDone.setSuccessful(false);
				//		return false;
				//	}
				//	newFile = new File(filename);
				// rename temporary filename to filename
				//	if(temporaryFile.renameTo(newFile) == false)
				//	{
				//		String errorString = new String(twilightCalibrateCommand.getId()+
				//			":Failed to rename '"+temporaryFile.toString()+"' to '"+
				//			newFile.toString()+"'.");

				//		loci.error(this.getClass().getName()+":doFrame:"+errorString);
				//		twilightCalibrateDone.
				//			setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2316);
				//		twilightCalibrateDone.setErrorString(errorString);
				//		twilightCalibrateDone.setSuccessful(false);
				//		return false;
				//	}// end if renameTo failed
				// reset twilight calibrate done's pipelined processed filename
				//	twilightCalibrateDone.setFilename(filename);
				// log rename
				//	loci.log(Logging.VERBOSITY_VERBOSE,
				//	      "Command:"+twilightCalibrateCommand.getId()+
				//	      ":doFrame:"+"bin:"+bin+
				//	      ":filter:"+filter+
				//	      ":Exposure DpRt frame rename:renamed "+temporaryFile+" to "+newFile+".");
				//}// end if temporary file exists
			//}// end if frameState was OK
		// Test abort status.
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
		// Find optimal exposure length to get the best number of mean counts
			optimalExposureLength = (int)(((float) exposureLength) * 
						      (((float)(bestMeanCounts[bin]))/meanCounts));
		// Bracket the optimal exposure length to an allowed exposure length
			exposureLength = optimalExposureLength;
			if(optimalExposureLength < minExposureLength)
				exposureLength = minExposureLength;
			else if(optimalExposureLength > maxExposureLength)
				exposureLength = maxExposureLength;
		// calculate the predicted mean counts for the bracketed exposure length
			predictedMeanCounts = meanCounts * (exposureLength/lastExposureLength);
			loci.log(Logging.VERBOSITY_VERBOSE,
			      "Command:"+twilightCalibrateCommand.getId()+
			      ":doFrame:"+"bin:"+bin+
			      ":filter:"+filter+
			      ":New Optimal exposure length:"+optimalExposureLength+
			      ":New limited exposure length:"+exposureLength+
			      ":Predicted mean counts:"+predictedMeanCounts+".");
		 // check the predicted mean counts for the bracketed exposure length
		 // are within the mean counts limits, otherwise assume the next exposure
		 // will return out of range mean counts and move onto the next calibration.
			if((timeOfNight == TIME_OF_NIGHT_SUNSET)&&(optimalExposureLength > maxExposureLength)&&
			   (predictedMeanCounts < minMeanCounts[bin]))
			{
				loci.log(Logging.VERBOSITY_VERBOSE,
					 "Command:"+twilightCalibrateCommand.getId()+
					 ":doFrame:"+"bin:"+bin+
					 ":filter:"+filter+
					 ":Predicted mean counts "+predictedMeanCounts+
					 " out of range(too low):moving to next calibration.");
				// try next calibration
				doneFrame = true;
				doneOffset = true;
				return true; 
			}
			if((timeOfNight == TIME_OF_NIGHT_SUNRISE)&&(optimalExposureLength < minExposureLength)&&
			   (predictedMeanCounts > maxMeanCounts[bin]))
			{
				loci.log(Logging.VERBOSITY_VERBOSE,
				      "Command:"+twilightCalibrateCommand.getId()+
				      ":doFrame:"+"bin:"+bin+
				      ":filter:"+filter+
				      ":Predicted mean counts "+predictedMeanCounts+
				      " out of range(too high):moving to next calibration.");
				// try next calibration
				doneFrame = true;
				doneOffset = true;
				return true; 
			}
		// send dp_ack, filename/mean counts/peak counts are all retrieved from twilightCalibrateDone,
		// which had these parameters filled in by reduceCalibrate
		// time to complete is readout overhead + exposure Time for next frame
			if(sendTwilightCalibrateDpAck(twilightCalibrateCommand,twilightCalibrateDone,
				exposureLength+frameOverhead) == false)
				return false;
		// Test abort status.
			if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
				return false;
		// check loop termination
			now = System.currentTimeMillis();
			if(frameState == FRAME_STATE_OK)
			{
				doneFrame = true;
				calibrationFrameCount++;
			// log
				loci.log(Logging.VERBOSITY_VERBOSE,"Command:"+twilightCalibrateCommand.getId()+
				      ":doFrame:"+"bin:"+bin+
				      ":filter:"+filter+
				      ":Frame completed.");
			}
			// have we run out of twilight calibrate time?
			// NB test at end to use recalculated exposure length
			if((now+exposureLength+frameOverhead) > 
				(implementationStartTime+twilightCalibrateCommand.getTimeToComplete()))
			{
			// try next calibration
				doneFrame = true;
				doneOffset = true;
			// log
				loci.log(Logging.VERBOSITY_VERBOSE,
				      "Command:"+twilightCalibrateCommand.getId()+
				      ":doFrame:"+"bin:"+bin+
				      ":filter:"+filter+
				      ":Ran out of time to complete:((now:"+now+
				      ")+(exposureLength:"+exposureLength+
				      ")+(frameOverhead:"+frameOverhead+")) > "+
				      "((implementationStartTime:"+implementationStartTime+
				      ")+(timeToComplete:"+twilightCalibrateCommand.getTimeToComplete()+")).");
			}
		}// end while !doneFrame
		return true;
	}

	/**
	 * Send a 'takeExposure' command to the loci-ctrl CCD Flask API.
	 * <ul>
	 * <li>We call getCCDFlaskConnectionData to setup ccdFlaskHostname and ccdFlaskPortNumber.
	 * <li>We retrieve the temporary filename to use from the "loci.twilight_calibrate.file.tmp" status property.
	 * <li>We setup and configure an instance of TakeExposureCommand, 
	 *     with connection details, exposure length, a temporary filename and "sky-flat" exposure type.
	 * <li>We run the instance of TakeExposureCommand.
	 * <li>We check whether a run exception occured, and throw it as an exception if so.
	 * <li>We log the return status and message.
	 * <li>We check whether the TakeExposureCommand return status was Success, and throw an exception if it
	 *     returned a failure.
	 * <li>We return the generated exposure filename.
	 * </ul>
	 * @param exposureLength The dark exposure length in milliseconds.
	 * @return The generated FITS filename is returned.
	 * @see #getCCDFlaskConnectionData
	 * @see #status
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.TakeExposureCommand
	 * @exception UnknownHostException Thrown if the address passed to TakeExposureCommand.setAddress is not a 
	 *            valid host.
	 * @exception Exception Thrown if the TakeExposureCommand generates a run exception, or the return
	 *            status is not success.
	 */
	protected String sendTakeExposureCommand(int exposureLength) throws UnknownHostException, Exception
	{
		TakeExposureCommand takeExposureCommand = null;
		String temporaryFilename = null;
		double exposureLengthS;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeExposureCommand:started with exposure length "+
			 exposureLength+" ms.");
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// get the temporary filename
		temporaryFilename = status.getProperty("loci.twilight_calibrate.file.tmp");
		// convert exposure length from milliseconds to decimal seconds
		exposureLengthS = ((double)exposureLength)/((double)LociConstants.MILLISECONDS_PER_SECOND);
		loci.log(Logging.VERBOSITY_INTERMEDIATE,"sendTakeExposureCommand:Exposure length: "+
			 exposureLengthS+" seconds.");
		// setup TakeExposureCommand
		takeExposureCommand = new TakeExposureCommand();
		takeExposureCommand.setAddress(ccdFlaskHostname);
		takeExposureCommand.setPortNumber(ccdFlaskPortNumber);
		takeExposureCommand.setExposureLength(exposureLengthS);
		takeExposureCommand.setTemporaryFile(temporaryFilename);
		takeExposureCommand.setExposureType("sky-flat");
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
	 * Method to send an instance of ACK back to the client. This stops the client timing out, whilst we
	 * work out what calibration to attempt next.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @return The method returns true if the ACK was sent successfully, false if an error occured.
	 */
	protected boolean sendBasicAck(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				       TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int timeToComplete)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(twilightCalibrateCommand.getId());
		acknowledge.setTimeToComplete(timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime());
		loci.log(Logging.VERBOSITY_VERBOSE,"Command:"+twilightCalibrateCommand.getId()+":sendBasicAck(time="+
		      (timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime())+").");
		try
		{
			serverConnectionThread.sendAcknowledge(acknowledge,true);
		}
		catch(IOException e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":sendBasicAck:Sending ACK failed:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2317);
			twilightCalibrateDone.setErrorString(errorString+e);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to send an instance of TWILIGHT_CALIBRATE_ACK back to the client. This tells the client about
	 * a FITS frame that has been produced, and also stops the client timing out.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @param filename The FITS filename to be sent back to the client, that has just completed
	 * 	processing.
	 * @return The method returns true if the ACK was sent successfully, false if an error occured.
	 */
	protected boolean sendTwilightCalibrateAck(TWILIGHT_CALIBRATE twilightCalibrateCommand,
						   TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,
						   int timeToComplete,String filename)
	{
		TWILIGHT_CALIBRATE_ACK twilightCalibrateAck = null;

	// send acknowledge to say frame is completed.
		twilightCalibrateAck = new TWILIGHT_CALIBRATE_ACK(twilightCalibrateCommand.getId());
		twilightCalibrateAck.setTimeToComplete(timeToComplete+
			serverConnectionThread.getDefaultAcknowledgeTime());
		twilightCalibrateAck.setFilename(filename);
		loci.log(Logging.VERBOSITY_VERBOSE,"Command:"+twilightCalibrateCommand.getId()+
		      ":sendTwilightCalibrateAck(time="+
		      (timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime())+",filename="+filename+").");
		try
		{
			serverConnectionThread.sendAcknowledge(twilightCalibrateAck,true);
		}
		catch(IOException e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":sendTwilightCalibrateAck:Sending TWILIGHT_CALIBRATE_ACK failed:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2318);
			twilightCalibrateDone.setErrorString(errorString+e);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to send an instance of TWILIGHT_CALIBRATE_DP_ACK back to the client. This tells the client about
	 * a FITS frame that has been produced, and the mean and peak counts in the frame.
	 * The time to complete parameter stops the client timing out.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * 	It also contains the filename and mean and peak counts returned from the last reduction calibration.
	 * @param timeToComplete The time it will take to complete the next set of operations
	 *	before the next ACK or DONE is sent to the client. The time is in milliseconds. 
	 * 	The server connection thread's default acknowledge time is added to the value before it
	 * 	is sent to the client, to allow for network delay etc.
	 * @return The method returns true if the ACK was sent successfully, false if an error occured.
	 */
	protected boolean sendTwilightCalibrateDpAck(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone,int timeToComplete)
	{
		TWILIGHT_CALIBRATE_DP_ACK twilightCalibrateDpAck = null;

	// send acknowledge to say frame is completed.
		twilightCalibrateDpAck = new TWILIGHT_CALIBRATE_DP_ACK(twilightCalibrateCommand.getId());
		twilightCalibrateDpAck.setTimeToComplete(timeToComplete+
			serverConnectionThread.getDefaultAcknowledgeTime());
		twilightCalibrateDpAck.setFilename(twilightCalibrateDone.getFilename());
		twilightCalibrateDpAck.setMeanCounts(twilightCalibrateDone.getMeanCounts());
		twilightCalibrateDpAck.setPeakCounts(twilightCalibrateDone.getPeakCounts());
		loci.log(Logging.VERBOSITY_VERBOSE,"Command:"+twilightCalibrateCommand.getId()+
		      ":sendTwilightCalibrateDpAck(time="+
		      (timeToComplete+serverConnectionThread.getDefaultAcknowledgeTime())+",filename="+
		      twilightCalibrateDone.getFilename()+
		      ",mean counts="+twilightCalibrateDone.getMeanCounts()+
		      ",peak counts="+twilightCalibrateDone.getPeakCounts()+").");
		try
		{
			serverConnectionThread.sendAcknowledge(twilightCalibrateDpAck,true);
		}
		catch(IOException e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":sendTwilightCalibrateDpAck:Sending TWILIGHT_CALIBRATE_DP_ACK failed:");
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2319);
			twilightCalibrateDone.setErrorString(errorString+e);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Private inner class that deals with loading and interpreting the saved state of calibrations
	 * (the TWILIGHT_CALIBRATE calibration database).
	 */
	private class TWILIGHT_CALIBRATESavedState
	{
		private NGATProperties properties = null;

		/**
		 * Constructor.
		 */
		public TWILIGHT_CALIBRATESavedState()
		{
			super();
			properties = new NGATProperties();
		}

		/**
	 	 * Load method, that retrieves the saved state from file.
		 * Calls the <b>properties</b> load method.
		 * @param filename The filename to load the saved state from.
		 * @exception FileNotFoundException Thrown if the file described by filename does not exist.
		 * @exception IOException Thrown if an IO error occurs whilst reading the file.
		 * @see #properties
	 	 */
		public void load(String filename) throws FileNotFoundException, IOException
		{
			properties.load(filename);
		}

		/**
	 	 * Save method, that stores the saved state into a file.
		 * Calls the <b>properties</b> load method.
		 * @param filename The filename to save the saved state to.
		 * @exception FileNotFoundException Thrown if the file described by filename does not exist.
		 * @exception IOException Thrown if an IO error occurs whilst writing the file.
		 * @see #properties
	 	 */
		public void save(String filename) throws IOException
		{
			Date now = null;

			now = new Date();
			properties.save(filename,"TWILIGHT_CALIBRATE saved state saved on:"+now);
		}

		/**
		 * Method to get the last time a calibration with these attributes was done.
		 * @param bin The binning factor used for this calibration.
		 * @param filter The filter type string used for this calibration.
		 * @return The number of milliseconds since the EPOCH, the last time a calibration with these
		 * 	parameters was completed. If this calibraion has not been performed before, zero
		 * 	is returned.
		 * @see #LIST_KEY_STRING
		 * @see #LIST_KEY_LAST_TIME_STRING
		 */
		public long getLastTime(int bin,String filter)
		{
			long time;

			try
			{
				time = properties.getLong(LIST_KEY_STRING+LIST_KEY_LAST_TIME_STRING+bin+"."+filter);
			}
			catch(NGATPropertyException e)/* assume failure due to key not existing */
			{
				time = 0;
			}
			return time;
		}

		/**
		 * Method to set the last time a calibration with these attributes was done.
		 * The time is set to now. The property file should be saved after a call to this method is made.
		 * @param bin The binning factor used for this calibration.
		 * @param filter The filter type string used for this calibration.
		 * @see #LIST_KEY_STRING
		 * @see #LIST_KEY_LAST_TIME_STRING
		 */
		public void setLastTime(int bin,String filter)
		{
			long now;

			now = System.currentTimeMillis();
			properties.setProperty(LIST_KEY_STRING+LIST_KEY_LAST_TIME_STRING+bin+"."+filter,
					       new String(""+now));
		}
	}// end class TWILIGHT_CALIBRATESavedState

	/**
	 * Private inner class that stores data pertaining to one possible calibration run that can take place during
	 * a TWILIGHT_CALIBRATE command invocation.
	 */
	private class TWILIGHT_CALIBRATECalibration
	{
		/**
		 * What binning to configure the ccd to for this calibration.
		 */
		protected int bin;
		/**
		 * The filter to use in the filter wheel.
		 */
		protected String filter = null;
		/**
		 * How often we should perform the calibration in milliseconds.
		 */
		protected long frequency;
		/**
		 * How sensitive is the filter (combination) to twilight daylight,
		 * as compared to no filters (1.0). A double between zero and one.
		 */
		protected double filterSensitivity = 0.0;
		/**
		 * The last time this calibration was performed. This is retrieved from the saved state,
		 * not from the calibration list.
		 */
		protected long lastTime;
		
		/**
		 * Constructor.
		 */
		public TWILIGHT_CALIBRATECalibration()
		{
			super();
		}

		/**
		 * Method to set the binning configuration for this calibration.
		 * @param b The binning to use. This should be greater than 0 and less than 5.
		 * @exception IllegalArgumentException Thrown if parameter b is out of range.
		 * @see #bin
		 */
		public void setBin(int b) throws IllegalArgumentException
		{
			if((b < 1)||(b > 4))
			{
				throw new IllegalArgumentException(this.getClass().getName()+":setBin failed:"+
					b+" not a legal binning value.");
			}
			bin = b;
		}

		/**
		 * Method to get the binning configuration for this calibration.
		 * @return The binning.
		 * @see #bin
		 */
		public int getBin()
		{
			return bin;
		}

		/**
		 * Method to set the filter type name.
	 	 * @param s The name to use.
		 * @exception NullPointerException Thrown if the filter string was null.
		 */
		public void setFilter(String s) throws NullPointerException
		{
			if(s == null)
			{
				throw new NullPointerException(this.getClass().getName()+
						":setFilter:Filter was null.");
			}
			filter = s;
		}

		/**
		 * Method to return the filter type for this calibration.
		 * @return A string containing the filter string.
		 */
		public String getFilter()
		{
			return filter;
		}

		/**
		 * Method to set the frequency this calibration should be performed.
		 * @param f The frequency in milliseconds. This should be greater than zero.
		 * @exception IllegalArgumentException Thrown if parameter f is out of range.
		 * @see #frequency
		 */
		public void setFrequency(long f) throws IllegalArgumentException
		{
			if(f <= 0)
			{
				throw new IllegalArgumentException(this.getClass().getName()+":setFrequency failed:"+
					f+" not a legal frequency.");
			}
			frequency = f;
		}

		/**
		 * Method to get the frequency configuration for this calibration.
		 * @return The frequency this calibration should be performed, in milliseconds.
		 * @see #frequency
		 */
		public long getFrequency()
		{
			return frequency;
		}

		/**
		 * Method to set the relative filter sensitivity of the filters at twilight in this calibration.
		 * @param d The relative filter sensitivity, compared to no filters. 
		 * 	This should be greater than zero and less than 1.0 (inclusive).
		 * @exception IllegalArgumentException Thrown if parameter d is out of range.
		 * @see #filterSensitivity
		 */
		public void setFilterSensitivity(double d) throws IllegalArgumentException
		{
			if((d < 0.0)||(d > 1.0))
			{
				throw new IllegalArgumentException(this.getClass().getName()+
					":setFilterSensitivity failed:"+d+" not a legal relative sensitivity.");
			}
			filterSensitivity = d;
		}

		/**
		 * Method to get the relative filter sensitivity of the filters at twilight for this calibration.
		 * @return The relative filter sensitivity of the filters, between 0.0 and 1.0, where 1.0 is
		 * 	the sensitivity no filters have.
		 * @see #filterSensitivity
		 */
		public double getFilterSensitivity()
		{
			return filterSensitivity;
		}

		/**
		 * Method to set the last time this calibration was performed.
		 * @param t A long representing the last time the calibration was done, as a 
		 * 	number of milliseconds since the EPOCH.
		 * @exception IllegalArgumentException Thrown if parameter f is out of range.
		 * @see #frequency
		 */
		public void setLastTime(long t) throws IllegalArgumentException
		{
			if(t < 0)
			{
				throw new IllegalArgumentException(this.getClass().getName()+":setLastTime failed:"+
					t+" not a legal last time.");
			}
			lastTime = t;
		}

		/**
		 * Method to get the last time this calibration was performed.
		 * @return The number of milliseconds since the epoch that this calibration was last performed.
		 * @see #frequency
		 */
		public long getLastTime()
		{
			return lastTime;
		}
	}// end class TWILIGHT_CALIBRATECalibration

	/**
	 * Private inner class that stores data pertaining to one telescope offset.
	 */
	private class TWILIGHT_CALIBRATEOffset
	{
		/**
		 * The offset in RA, in arcseconds.
		 */
		protected float raOffset;
		/**
		 * The offset in DEC, in arcseconds.
		 */
		protected float decOffset;
		
		/**
		 * Constructor.
		 */
		public TWILIGHT_CALIBRATEOffset()
		{
			super();
		}

		/**
		 * Method to set the offset in RA.
		 * @param o The offset in RA, in arcseconds, to use. This parameter must be in the range
		 * 	[-3600..3600] arcseconds.
		 * @exception IllegalArgumentException Thrown if parameter o is out of range.
		 * @see #raOffset
		 */
		public void setRAOffset(float o) throws IllegalArgumentException
		{
			if((o < -3600)||(o > 3600))
			{
				throw new IllegalArgumentException(this.getClass().getName()+":setRAOffset failed:"+
					o+" out of range.");
			}
			raOffset = o;
		}

		/**
		 * Method to get the offset in RA.
		 * @return The offset, in arcseconds.
		 * @see #raOffset
		 */
		public float getRAOffset()
		{
			return raOffset;
		}

		/**
		 * Method to set the offset in DEC.
		 * @param o The offset in DEC, in arcseconds, to use. This parameter must be in the range
		 * 	[-3600..3600] arcseconds.
		 * @exception IllegalArgumentException Thrown if parameter o is out of range.
		 * @see #decOffset
		 */
		public void setDECOffset(float o) throws IllegalArgumentException
		{
			if((o < -3600)||(o > 3600))
			{
				throw new IllegalArgumentException(this.getClass().getName()+":setDECOffset failed:"+
					o+" out of range.");
			}
			decOffset = o;
		}

		/**
		 * Method to get the offset in DEC.
		 * @return The offset, in arcseconds.
		 * @see #decOffset
		 */
		public float getDECOffset()
		{
			return decOffset;
		}
	}// end TWILIGHT_CALIBRATEOffset
}
