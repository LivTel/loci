// HardwareImplementation.java
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
 * This class provides some common hardware related routines to move folds, and FITS
 * interface routines needed by many command implementations
 * @version $Revision: HardwareImplementation.java $
 */
public class HardwareImplementation extends CommandImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The host the loci-ctrl CCD Flask end-point is located on.
	 */
	protected String ccdFlaskHostname = null;
	/**
	 * The port number the loci-ctrl CCD Flask end-point is located on.
	 */
	protected int ccdFlaskPortNumber;

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
	 * This routine tries to move the mirror fold to a certain location, by issuing a MOVE_FOLD command
	 * to the ISS. The position to move the fold to is specified by the loci property file.
	 * If an error occurs the done objects field's are set accordingly.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see LociStatus#getPropertyInteger
	 * @see Loci#sendISSCommand
	 */
	public boolean moveFold(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		MOVE_FOLD moveFold = null;
		int mirrorFoldPosition = 0;

		moveFold = new MOVE_FOLD(command.getId());
		try
		{
			mirrorFoldPosition = status.getPropertyInteger("loci.mirror_fold_position");
		}
		catch(NumberFormatException e)
		{
			mirrorFoldPosition = 0;
			loci.error(this.getClass().getName()+":moveFold:"+
				   command.getClass().getName(),e);
			done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1201);
			done.setErrorString("moveFold:"+e);
			done.setSuccessful(false);
			return false;
		}
		moveFold.setMirror_position(mirrorFoldPosition);
		instToISSDone = loci.sendISSCommand(moveFold,serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			loci.error(this.getClass().getName()+":moveFold:"+
				   command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1202);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);		
			return false;
		}
		return true;
	}

	/**
	 * This routine clears the current set of FITS headers. 
	 * <ul>
	 * <li>We call getCCDFlaskConnectionData to get the Flask API configuration.
	 * <li>We construct an instance of ClearHeaderKeywordsCommand, configure it, run the command,
	 *     and check the reply.
	 * </ul>
	 * @see #getCCDFlaskConnectionData
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see ngat.loci.ccd.ClearHeaderKeywordsCommand
	 * @exception Exception Thrown if ClearHeaderKeywordsCommand throws an exception, or returns a status that is
	 *            not success.
	 */
	public void clearFitsHeaders() throws Exception
	{
		ClearHeaderKeywordsCommand clearHeaderKeywordsCommand = null;
		
		// get CCD Flask API connection data
		getCCDFlaskConnectionData();
		// setup
		clearHeaderKeywordsCommand = new ClearHeaderKeywordsCommand();
		clearHeaderKeywordsCommand.setAddress(ccdFlaskHostname);
		clearHeaderKeywordsCommand.setPortNumber(ccdFlaskPortNumber);
		// run command
		clearHeaderKeywordsCommand.run();
		// check reply
		if(clearHeaderKeywordsCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+
					    ":clearFitsHeaders:ClearHeaderKeywordsCommand Failed:",
					    clearHeaderKeywordsCommand.getRunException());
		}
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "clearFitsHeaders:Clear Fits Header Command Finished with status: "+
			 clearHeaderKeywordsCommand.getReturnStatus()+
			 " and message:"+clearHeaderKeywordsCommand.getMessage()+".");
		if(clearHeaderKeywordsCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":clearFitsHeaders:Clear Fits Header Command failed with status: "+
					    clearHeaderKeywordsCommand.getReturnStatus()+
					    " and message:"+clearHeaderKeywordsCommand.getMessage()+".");
		}
	}

	/**
	 * This routine gets a set of FITS header from a config file. The retrieved FITS headers are added to the 
	 * C layer. The "loci.fits.keyword.&lt;n&gt;" properties is queried in ascending order
	 * of &lt;n&gt; to find keywords.
	 * The "loci.fits.value.&lt;keyword&gt;" property contains the value of the keyword.
	 * The value's type is retrieved from the property "loci.fits.value.type.&lt;keyword&gt;", 
	 * which should comtain one of the following values: boolean|float|integer|string.
	 * The addFitsHeader method is then called to actually add the FITS header to the C layer.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param commandDone A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #addFitsHeader
	 */
	public boolean setFitsHeaders(COMMAND command,COMMAND_DONE commandDone)
	{
		String keyword = null;
		String typeString = null;
		String valueString = null;
		String commentString = null;
		String unitsString = null;
		boolean done;
		double dvalue;
		int index,ivalue;
		boolean bvalue;

		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":setFitsHeaders:Started.");
		index = 0;
		done = false;
		while(done == false)
		{
			loci.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				 ":setFitsHeaders:Looking for keyword index "+index+" in list.");
			keyword = status.getProperty("loci.fits.keyword."+index);
			if(keyword != null)
			{
				// value type
				typeString = status.getProperty("loci.fits.value.type."+keyword);
				if(typeString == null)
				{
					loci.error(this.getClass().getName()+
						   ":setFitsHeaders:Failed to get value type for keyword:"+keyword);
					commandDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1203);
					commandDone.setErrorString(this.getClass().getName()+
						   ":setFitsHeaders:Failed to get value type for keyword:"+keyword);
					commandDone.setSuccessful(false);
					return false;
				}
				// comment
				commentString = status.getProperty("loci.fits.value.comment."+keyword);
				// comment can be null if no comment exists
				// units
				unitsString = status.getProperty("loci.fits.value.units."+keyword);
				// comment can be null if no units exist
				// Add FITS header
				try
				{
					if(typeString.equals("string"))
					{
						valueString = status.getProperty("loci.fits.value."+keyword);
						addFitsHeader(keyword,valueString,commentString,unitsString);
					}
					else if(typeString.equals("integer"))
					{
						Integer iov = null;
						
						ivalue = status.getPropertyInteger("loci.fits.value."+keyword);
						iov = new Integer(ivalue);
						addFitsHeader(keyword,iov,commentString,unitsString);
					}
					else if(typeString.equals("float"))
					{
						Float fov = null;
							
						dvalue = status.getPropertyDouble("loci.fits.value."+keyword);
						fov = new Float(dvalue);
						addFitsHeader(keyword,fov,commentString,unitsString);
					}
					else if(typeString.equals("boolean"))
					{
						Boolean bov = null;
						
						bvalue = status.getPropertyBoolean("loci.fits.value."+keyword);
						bov = new Boolean(bvalue);
						addFitsHeader(keyword,bov,commentString,unitsString);
					}
					else
					{
						loci.error(this.getClass().getName()+
							   ":setFitsHeaders:Unknown value type "+typeString+
							   " for keyword:"+keyword);
						commandDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+
									1204);
						commandDone.setErrorString(this.getClass().getName()+
									   ":setFitsHeaders:Unknown value type "+
									   typeString+" for keyword:"+keyword);
						commandDone.setSuccessful(false);
						return false;
					}
				}
				catch(Exception e)
				{
					loci.error(this.getClass().getName()+
						     ":setFitsHeaders:Failed to add value for keyword:"+
						     keyword,e);
					commandDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1206);
					commandDone.setErrorString(this.getClass().getName()+
								   ":setFitsHeaders:Failed to add value for keyword:"+
								   keyword+":"+e);
					commandDone.setSuccessful(false);
					return false;
				}
				// increment index
				index++;
			}
			else
				done = true;
		}// end while
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":setFitsHeaders:Finished.");
		return true;
	}

	/**
	 * This routine tries to get a set of FITS headers for an exposure, by issuing a GET_FITS command
	 * to the ISS. The results from this command are put into the C layers list of FITS headers by calling
	 * addISSFitsHeaderList.
	 * If an error occurs the done objects field's can be set to record the error.
	 * @param command The command being implemented that made this call to the ISS. This is used
	 * 	for error logging.
	 * @param done A COMMAND_DONE subclass specific to the command being implemented. If an
	 * 	error occurs the relevant fields are filled in with the error.
	 * @return The routine returns a boolean to indicate whether the operation was completed
	 *  	successfully.
	 * @see #addISSFitsHeaderList
	 * @see Loci#sendISSCommand
	 * @see Loci#getStatus
	 * @see LociStatus#getPropertyInteger
	 */
	public boolean getFitsHeadersFromISS(COMMAND command,COMMAND_DONE done)
	{
		INST_TO_ISS_DONE instToISSDone = null;
		GET_FITS_DONE getFitsDone = null;
		FitsHeaderCardImage cardImage = null;
		Object value = null;
		Vector list = null;
		int orderNumberOffset;

		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":getFitsHeadersFromISS:Started.");
		instToISSDone = loci.sendISSCommand(new GET_FITS(command.getId()),serverConnectionThread);
		if(instToISSDone.getSuccessful() == false)
		{
			loci.error(this.getClass().getName()+":getFitsHeadersFromISS:"+
				     command.getClass().getName()+":"+instToISSDone.getErrorString());
			done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1205);
			done.setErrorString(instToISSDone.getErrorString());
			done.setSuccessful(false);
			return false;
		}
	// Get the returned FITS header information into the FitsHeader object.
		getFitsDone = (GET_FITS_DONE)instToISSDone;
	// extract specific FITS headers and add them to the C layer's list
		list = getFitsDone.getFitsHeader();
		try
		{
			addISSFitsHeaderList(list);
		}
		catch(Exception e)
		{
			loci.error(this.getClass().getName()+
				     ":getFitsHeadersFromISS:addISSFitsHeaderList failed.",e);
			done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1207);
			done.setErrorString(this.getClass().getName()+
					    ":getFitsHeadersFromISS:addISSFitsHeaderList failed:"+e);
			done.setSuccessful(false);
			return false;
		}
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":getFitsHeadersFromISS:finished.");
		return true;
	}

	/**
	 * Try to extract the GET_FITS headers returned from the ISS (RCS),
	 * and pass them onto the C layer.
	 * @param list A Vector of FitsHeaderCardImage instances. These will be passed to the C layer.
	 * @exception Exception Thrown if addFitsHeader fails.
	 * @see #addFitsHeader
	 * @see ngat.fits.FitsHeaderCardImageKeywordComparator
	 * @see ngat.fits.FitsHeaderCardImage
	 */
	protected void addISSFitsHeaderList(List list) throws Exception
	{
		FitsHeaderCardImage cardImage = null;
		
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			 ":addISSFitsHeaderList:started.");
		// iterate over keywords to copy
		for(int index = 0; index < list.size(); index ++)
		{
			cardImage = (FitsHeaderCardImage)(list.get(index));
			loci.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				 ":addISSFitsHeaderList:Adding "+cardImage.getKeyword()+" to C layer.");
			addFitsHeader(cardImage.getKeyword(),cardImage.getValue(),
				      cardImage.getComment(),cardImage.getUnits());
		}// end for
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+":addISSFitsHeaderList:finished.");
	}

	/**
	 * Method to add the specified FITS header to the C layers list of FITS headers. 
	 * @param keyword The FITS headers keyword.
	 * @param value The FITS headers value - an object of class String,Integer,Float,Double,Boolean,Date.
	 * @param commentString A string describing a comment to go in the FITS header card. 
	 *        This can have a zero length or even bu null if no comment is required.
	 * @param unitsString A string describing the units to go in the FITS header card 
	 *        (at the beginning of the comment). 
	 *        This can have a zero length or even bu null if no units are required.
	 * @exception Exception Thrown if the Command internally errors, or the return code indicates a
	 *            failure.
	 * @see #status
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 * @see #dateFitsFieldToString
	 * @see ngat.loci.LociStatus#getProperty
	 * @see ngat.loci.LociStatus#getPropertyInteger
	 */
	protected void addFitsHeader(String keyword,Object value,
				     String commentString,String unitsString) throws Exception
	{
		SetHeaderKeywordCommand setHeaderKeywordCommand = null;
		int returnCode;
		String errorString = null;

		if(keyword == null)
		{
			throw new NullPointerException(this.getClass().getName()+":addFitsHeader:keyword was null.");
		}
		if(value == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":addFitsHeader:value was null for keyword:"+keyword);
		}
		// get Flask API parameters
		getCCDFlaskConnectionData();
		// create command
		setHeaderKeywordCommand = new SetHeaderKeywordCommand();
		setHeaderKeywordCommand.setAddress(ccdFlaskHostname);
		setHeaderKeywordCommand.setPortNumber(ccdFlaskPortNumber);
		// set command parameters
		setHeaderKeywordCommand.setKeyword(keyword);
		if(value instanceof String)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":addFitsHeader:Adding keyword "+keyword+" with String value "+value+".");
			setHeaderKeywordCommand.setValue((String)value);
		}
		else if(value instanceof Integer)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":addFitsHeader:Adding keyword "+keyword+" with integer value "+
				   ((Integer)value).intValue()+".");
			setHeaderKeywordCommand.setValue(((Integer)value).intValue());
		}
		else if(value instanceof Float)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				 ":addFitsHeader:Adding keyword "+keyword+" with float value "+
				   ((Float)value).doubleValue()+".");
			setHeaderKeywordCommand.setValue(((Float)value).doubleValue());
		}
		else if(value instanceof Double)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":addFitsHeader:Adding keyword "+keyword+" with double value "+
				   ((Double)value).doubleValue()+".");
		        setHeaderKeywordCommand.setValue(((Double)value).doubleValue());
		}
		else if(value instanceof Boolean)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":addFitsHeader:Adding keyword "+keyword+" with boolean value "+
				   ((Boolean)value).booleanValue()+".");
			setHeaderKeywordCommand.setValue(((Boolean)value).booleanValue());
		}
		else if(value instanceof Date)
		{
			loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
				   ":addFitsHeader:Adding keyword "+keyword+" with date value "+
				   dateFitsFieldToString((Date)value)+".");
			setHeaderKeywordCommand.setValue(dateFitsFieldToString((Date)value));
		}
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
							   ":addFitsHeader:value had illegal class:"+
							   value.getClass().getName());
		}
		// set comment and units if available
		if(commentString != null)
			setHeaderKeywordCommand.setComment(commentString);
		if(unitsString != null)
			setHeaderKeywordCommand.setUnits(unitsString);
		// actually send the command to the CCD flask API
		setHeaderKeywordCommand.run();
		// check whether a run exception occurred
		if(setHeaderKeywordCommand.getRunException() != null)
		{
			loci.error("addFitsHeader:SetHeaderKeywordCommand Failed:"+
				   setHeaderKeywordCommand.getRunException(),
				   setHeaderKeywordCommand.getRunException());
			throw new Exception(this.getClass().getName()+
					    ":addFitsHeader:SetHeaderKeywordCommand Failed:",
					    setHeaderKeywordCommand.getRunException());
		}
		// check the parsed reply
		loci.log(Logging.VERBOSITY_VERBOSE,
			 "addFitsHeader:Add Fits Header Command Finished with status: "+
			 setHeaderKeywordCommand.getReturnStatus()+
			 " and message:"+setHeaderKeywordCommand.getMessage()+".");
		if(setHeaderKeywordCommand.isReturnStatusSuccess() == false)
		{
			loci.error("addFitsHeader:Command failed with return code "+
				   setHeaderKeywordCommand.getReturnStatus()+
				   " and message:"+setHeaderKeywordCommand.getMessage());
			throw new Exception(this.getClass().getName()+
					    ":addFitsHeader:Set Fits Header Keyword Command failed with status: "+
					    setHeaderKeywordCommand.getReturnStatus()+
					    " and message:"+setHeaderKeywordCommand.getMessage()+".");
		}
	}

	/**
	 * Retrieve the loci-ctrl CCD Flask end-point conenction data.
	 * <ul>
	 * <li>Retrieve the loci-ctrl CCD Flask end-point hostname from the property 'loci.flask.ccd.hostname.
	 * <li>Retrieve the loci-ctrl CCD Flask end-point port number from the property 'loci.flask.ccd.port_number'.
	 * </ul>
	 * @see #status
	 * @see #ccdFlaskHostname
	 * @see #ccdFlaskPortNumber
	 */
	protected void getCCDFlaskConnectionData()
	{
		ccdFlaskHostname = status.getProperty("loci.flask.ccd.hostname");
		ccdFlaskPortNumber = status.getPropertyInteger("loci.flask.ccd.port_number");		
	}
	
	/**
	 * This routine takes a Date, and formats a string to the correct FITS format for that date and returns it.
	 * The format should be 'CCYY-MM-DDThh:mm:ss[.sss...]'.
	 * @param date The date to return a string for.
	 * @return Returns a String version of the date in the correct new FITS format.
	 */
	private String dateFitsFieldToString(Date date)
	{
		Calendar calendar = Calendar.getInstance();
		NumberFormat numberFormat = NumberFormat.getInstance();

		numberFormat.setMinimumIntegerDigits(2);
		calendar.setTime(date);
		return new String(calendar.get(Calendar.YEAR)+"-"+
			numberFormat.format(calendar.get(Calendar.MONTH)+1)+"-"+
			numberFormat.format(calendar.get(Calendar.DAY_OF_MONTH))+"T"+
			numberFormat.format(calendar.get(Calendar.HOUR_OF_DAY))+":"+
			numberFormat.format(calendar.get(Calendar.MINUTE))+":"+
			numberFormat.format(calendar.get(Calendar.SECOND))+"."+
			calendar.get(Calendar.MILLISECOND));
	}
}
