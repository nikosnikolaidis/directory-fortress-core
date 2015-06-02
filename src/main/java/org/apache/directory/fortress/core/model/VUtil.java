/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.fortress.core.model;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.directory.fortress.core.util.ObjUtil;
import org.slf4j.LoggerFactory;
import org.apache.directory.fortress.core.GlobalErrIds;
import org.apache.directory.fortress.core.GlobalIds;
import org.apache.directory.fortress.core.ValidationException;
import org.apache.directory.fortress.core.util.Config;


/**
 * This class contains simple data validation utilities.  The data validations include null, length
 * and simple reasonability checking.  All utilities will throw {@link ValidationException} for failures.
 */
public final class VUtil
{
    private static final String CLS_NM = VUtil.class.getName();
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger( CLS_NM );
    private static int maximumFieldLen = 130;
    private static final String VALIDATE_LENGTH = "field.length";

    static
    {
        String lengthProp = Config.getProperty( VALIDATE_LENGTH );
        try
        {
            if ( lengthProp != null )
            {
                Integer len = Integer.valueOf( lengthProp );
                maximumFieldLen = len;
            }
        }
        catch ( java.lang.NumberFormatException nfe )
        {
            //ignore
        }
    }

    private static final int MAXIMUM_FIELD_LEN = maximumFieldLen;
    private static final int maxFieldLength = MAXIMUM_FIELD_LEN;
    private static final int TIME_LEN = 4;
    private static final int DATE_LEN = 8;
    private static final int DAYMASK_LEN = 7;
    private static final String TIME_FORMAT = "HHmm";
    private static final String DATE_FORMAT = "yyyyMMdd";
    private static final char SUNDAY = '1';
    private static final char SATURDAY = '7';
    private static final SimpleDateFormat TIME_FORMATER = new SimpleDateFormat( TIME_FORMAT );
    private static final SimpleDateFormat DATE_FORMATER = new SimpleDateFormat( DATE_FORMAT );

    static
    {
        String lengthProp = Config.getProperty( VALIDATE_LENGTH );

        if ( lengthProp != null )
        {
            maximumFieldLen = Integer.parseInt( lengthProp );
        }

        TIME_FORMATER.setLenient( false );
        DATE_FORMATER.setLenient( false );
    }

    /**
     * Private constructor
     *
     */
    private VUtil()
    {
    }

    /**
     * Simple length check on orgunit that uses {@link org.apache.directory.fortress.core.GlobalIds#OU_LEN}.
     *
     * @param orgUnitId contains the ou name.
     * @throws ValidationException in the event of failure, {@link org.apache.directory.fortress.core.GlobalErrIds#ORG_LEN_INVLD}.
     */
    public static void orgUnit( String orgUnitId ) throws ValidationException
    {
        if ( !StringUtils.isNotEmpty( orgUnitId ) )
        {
            String error = "orgUnit is null";
            throw new ValidationException( GlobalErrIds.ORG_NULL, error );
        }
        int length = orgUnitId.length();

        if ( length > maxFieldLength )
        {
            String error = "orgUnit value [" + orgUnitId + "] invalid length [" + length + "]";
            throw new ValidationException( GlobalErrIds.ORG_LEN_INVLD, error );
        }
    }


    /**
     * Simple length check on User password that uses {@link org.apache.directory.fortress.core.GlobalIds#PASSWORD_LEN}.
     * @param password contains the User's password.
     * @throws ValidationException in the event of failure, {@link org.apache.directory.fortress.core.GlobalErrIds#USER_PW_INVLD_LEN}.
     */
    public static void password( char[] password ) throws ValidationException
    {
        int length = password.length;

        if ( length > GlobalIds.PASSWORD_LEN )
        {
            String error = "password invalid length [" + length + "]";
            throw new ValidationException( GlobalErrIds.USER_PW_INVLD_LEN, error );
        }
    }


    /**
     * Simple length check and safe text validation on description field that uses {@link org.apache.directory.fortress.core.GlobalIds#DESC_LEN}.
     *
     * @param value contains the entity description.
     * @throws org.apache.directory.fortress.core.ValidationException
     *          in the event of failure, {@link org.apache.directory.fortress.core.GlobalErrIds#CONST_DESC_LEN_INVLD}.
     */
    public static void description( String value ) throws ValidationException
    {
        int length = value.length();

        if ( length > GlobalIds.DESC_LEN )
        {
            String error = "description value [" + value + "] invalid length [" + length + "]";
            throw new ValidationException( GlobalErrIds.CONST_DESC_LEN_INVLD, error );
        }

        RegExUtil.safeText( value );
    }


    /**
     * Perform a simple length and safe text validation.
     *
     * @param value contains the attribute to check.
     * @param validLen contains the length to use.
     * @throws ValidationException in the event of length {@link org.apache.directory.fortress.core.GlobalErrIds#CONST_INVLD_FIELD_LEN} or regex failure.
     */
    public static void safeText( String value, int validLen ) throws ValidationException
    {
        if ( !StringUtils.isNotEmpty( value ) )
        {
            String error = "safeText null value";
            throw new ValidationException( GlobalErrIds.CONST_NULL_TEXT, error );
        }

        int length = value.length();

        if ( length > validLen )
        {
            String error = "safeText value [" + value + "] invalid length [" + length + "]";
            throw new ValidationException( GlobalErrIds.CONST_INVLD_FIELD_LEN, error );
        }

        RegExUtil.safeText( value );
    }


    /**
     * Simple null, {@link org.apache.directory.fortress.core.GlobalErrIds#USER_ID_NULL}, and length checks, {@link org.apache.directory.fortress.core.GlobalErrIds#CONST_INVLD_FIELD_LEN}, on userId.
     *
     * @param userId contains the userId, maps to {@link org.apache.directory.fortress.core.model.User#userId}.
     * @throws ValidationException in the event of failure, {@link GlobalErrIds#CONST_INVLD_FIELD_LEN}.
     */
    public static void userId( String userId ) throws ValidationException
    {
        if ( !StringUtils.isNotEmpty( userId ) )
        {
            String error = "userId validation failed, null or empty value";
            throw new ValidationException( GlobalErrIds.USER_ID_NULL, error );
        }

        int length = userId.length();

        if ( length > GlobalIds.USERID_LEN )
        {
            String error = "safeText value [" + userId + "] invalid length [" + length + "]";
            throw new ValidationException( GlobalErrIds.CONST_INVLD_FIELD_LEN, error );
        }
    }


    /**
     * Perform simple length and safe text validations on collection of name-value pairs.
     *
     * @param props contains name-value pairs in {@code name:value} format.
     * @throws ValidationException in the event of failure.
     */
    public static void properties( Properties props ) throws ValidationException
    {
        if ( ObjUtil.isNotNullOrEmpty( props ) )
        {
            for ( Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); )
            {
                String key = ( String ) e.nextElement();
                String val = props.getProperty( key );
                safeText( key, GlobalIds.PROP_LEN );
                safeText( val, GlobalIds.PROP_LEN );
            }
        }
    }


    /**
     * Perform simple reasonability check on contraint timeout value.
     *
     * @param timeout must be greater than 0 and less than max value for {@link Integer#MAX_VALUE}
     * @throws org.apache.directory.fortress.core.ValidationException
     *          in the event value falls out of range.
     */
    public static void timeout( Integer timeout ) throws ValidationException
    {
        if ( ( timeout < 0 ) || ( timeout >= Integer.MAX_VALUE ) )
        {
            String error = "timeout - invalid timeout value [" + timeout + "]";
            throw new ValidationException( GlobalErrIds.CONST_TIMEOUT_INVLD, error );
        }
    }


    /**
     * Perform simple reasonability check on contraint beginTime value.
     *
     * @param beginTime if set, must be equal to {@link #TIME_LEN}.
     * @throws org.apache.directory.fortress.core.ValidationException
     *          in the event value falls out of range.
     */
    public static void beginTime( String beginTime ) throws ValidationException
    {
        if ( ( beginTime != null ) && ( beginTime.length() == TIME_LEN ) )
        {
            if ( checkTime( beginTime ) )
            {
                String error = "beginTime - invalid beginTime value [" + beginTime + "]";
                throw new ValidationException( GlobalErrIds.CONST_BEGINTIME_INVLD, error );
            }
        }
        else
        {
            String error = "beginTime - null or invalid length (must be 4) for beginTime value";
            throw new ValidationException( GlobalErrIds.CONST_BEGINTIME_LEN_ERR, error );
        }
    }


    /**
     * Perform simple reasonability check on contraint endTime value.
     * @param endTime if set, must be equal to {@link #TIME_LEN}.
     * @throws ValidationException in the event value falls out of range.
     */
    public static void endTime( String endTime ) throws ValidationException
    {
        if ( ( endTime != null ) && ( endTime.length() == TIME_LEN ) )
        {
            if ( checkTime( endTime ) )
            {
                String error = "endTime - invalid endTime value [" + endTime + "]";
                throw new ValidationException( GlobalErrIds.CONST_ENDTIME_INVLD, error );
            }
        }
        else
        {
            String error = "endTime - null or invalid length (must be 4) for endTime value";
            throw new ValidationException( GlobalErrIds.CONST_ENDTIME_LEN_ERR, error );
        }
    }


    /**
     * Perform simple reasonability check on contraint beginDate value.
     * @param beginDate if set, must be equal to {@link #DATE_LEN}.
     * @throws ValidationException in the event value falls out of range.
     */
    public static void beginDate( String beginDate )
        throws ValidationException
    {
        if ( StringUtils.isNotEmpty( beginDate ) )
        {
            if ( ( beginDate.compareToIgnoreCase( GlobalIds.NONE ) != 0 ) &&
                ( ( beginDate.length() != DATE_LEN ) || checkDate( beginDate ) ) )
            {
                String error = "beginDate - invalid beginDate value [" + beginDate + "]";
                throw new ValidationException( GlobalErrIds.CONST_BEGINDATE_INVLD, error );
            }
        }
        else
        {
            String error = "beginDate - null or empty beginDate value";
            throw new ValidationException( GlobalErrIds.CONST_BEGINDATE_NULL, error );
        }
    }


    /**
     * Perform simple reasonability check on contraint endDate value.
     * @param endDate if set, must be equal to {@link #DATE_LEN}.
     * @throws ValidationException in the event value falls out of range.
     */
    public static void endDate( String endDate ) throws ValidationException
    {
        if ( StringUtils.isNotEmpty( endDate ) )
        {
            if ( endDate.compareToIgnoreCase( GlobalIds.NONE ) != 0 )
            {
                if ( endDate.length() != DATE_LEN || checkDate( endDate ) )
                {
                    String error = "endDate - invalid endDate value [" + endDate + "]";
                    throw new ValidationException( GlobalErrIds.CONST_ENDDATE_INVLD, error );
                }
            }
        }
        else
        {
            String error = "endDate - null or empty endDate value";
            throw new ValidationException( GlobalErrIds.CONST_ENDDATE_NULL, error );
        }
    }


    /**
     * Perform simple reasonability check on contraint dayMask value.
     * @param dayMask if set, will be validated.
     * @throws ValidationException in the event value falls out of range.
     */
    public static void dayMask( String dayMask ) throws ValidationException
    {
        if ( StringUtils.isNotEmpty( dayMask ) )
        {
            if ( dayMask.compareToIgnoreCase( GlobalIds.ALL ) != 0 )
            {
                if ( dayMask.length() > DAYMASK_LEN || checkMask( dayMask ) )
                {
                    String error = "dayMask - invalid dayMask value [" + dayMask + "]";
                    throw new ValidationException( GlobalErrIds.CONST_DAYMASK_INVLD, error );
                }
            }
        }
        else
        {
            String error = "dayMask - null or empty dayMask value";
            throw new ValidationException( GlobalErrIds.CONST_DAYMASK_NULL, error );
        }
    }


    /**
     * @param time
     * @return boolean
     */
    private static boolean checkTime( String time )
    {
        try
        {
            synchronized ( TIME_FORMATER )
            {
                TIME_FORMATER.parse( time );
                return false;
            }
        }
        catch ( ParseException pe )
        {
            String error = "checkTime - time [" + time + "] failed validation with ParseException=" + pe;
            LOG.warn( error );
            return true;
        }
    }


    /**
     * @param date
     * @return boolean
     */
    private static boolean checkDate( String date )
    {
        try
        {
            synchronized ( DATE_FORMATER )
            {
                DATE_FORMATER.parse( date );
                return false;
            }
        }
        catch ( ParseException pe )
        {
            String error = "checkDate - date [" + date + "] failed validation with ParseException=" + pe;
            LOG.warn( error );

            return true;
        }
    }


    /**
     * @param mask
     * @return boolean
     */
    private static boolean checkMask( String mask )
    {
        for ( char c : mask.toCharArray() )
        {
            if ( ( c < SUNDAY ) || ( c > SATURDAY ) )
            {
                String error = "checkMask - mask [" + mask + "] failed validation";
                LOG.warn( error );

                return true;
            }
        }

        return false;
    }


    /**
     * Method will throw exception with supplied error id and object.method name if object reference is null.
     *
     * @param obj       contains the reference to check.
     * @param errorCode contains the error id to use if null.
     * @param method contains the method name of caller.
     * @throws ValidationException in the event object is null.
     */
    public static void assertNotNull( Object obj, int errorCode, String method )
        throws ValidationException
    {
        if ( obj == null )
        {
            String error = "assertContext detected null entity for method [" + method + "], error code ["
                + errorCode + "]";
            throw new ValidationException( errorCode, error );
        }
    }


    /**
     * Method will throw exception with supplied error id and object.method name if string reference is null or empty.
     *
     * @param value     contains the reference to check.
     * @param errorCode contains the error id to use if null.
     * @param method contains the method name of caller.
     * @throws ValidationException in the event supplied string is null or empty.
     */
    public static void assertNotNullOrEmpty( String value, int errorCode, String method )
        throws ValidationException
    {
        if ( !StringUtils.isNotEmpty( value ) )
        {
            String error = "assertContext detected null entity for method [" + method + "], error code ["
                + errorCode + "]";
            throw new ValidationException( errorCode, error );
        }
    }


    /**
     * Method will throw exception with supplied error id and object.method name if string reference is null or empty.
     *
     * @param value     contains the reference to check.
     * @param errorCode contains the error id to use if null.
     * @param method contains the method name of caller.
     * @throws ValidationException in the event supplied string is null or empty.
     */
    public static void assertNotNullOrEmpty( char[] value, int errorCode, String method )
        throws ValidationException
    {
        if ( !ObjUtil.isNotNullOrEmpty( value ) )
        {
            String error = "assertContext detected null entity for method [" + method + "], error code ["
                + errorCode + "]";
            throw new ValidationException( errorCode, error );
        }
    }


/*
    */
/**
     * Method will return true if string array reference is not null or empty.
     *
     * @param value contains the reference to string array.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( String[] value )
    {
        return ( value != null ) && ( value.length > 0 );
    }


    */
/**
     * Method will return true if string reference is not null or empty.
     *
     * @param value contains the reference to string.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( char[] value )
    {
        return ( value != null ) && ( value.length > 0 );
    }


    */
/**
     * Method will return true if list is not null or empty.
     *
     * @param list contains the reference to list.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( Collection<?> list )
    {
        return ( list != null ) && ( list.size() > 0 );
    }


    */
/**
     * Method will return true if props is not null or empty.
     *
     * @param props contains the reference to props.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( Properties props )
    {
        return ( props != null ) && ( props.size() > 0 );
    }


    */
/**
     * Method will return true if input is not null or empty.
     *
     * @param iVal contains the reference to Integer variable.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( Integer iVal )
    {
        return ( iVal != null );
    }


    */
/**
     * Method will return true if input is not null or empty.
     *
     * @param bVal contains the reference to Boolean variable.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( Boolean bVal )
    {
        return ( bVal != null );
    }


    */
/**
     * Method will return true if byte array reference is not null or empty.
     *
     * @param value contains the reference to byte array.
     * @return boolean if validation succeeds.
     *//*

    public static boolean isNotNullOrEmpty( byte[] value )
    {
        boolean result = false;
        if ( value != null && value.length > 0 )
        {
            result = true;
        }
        return result;
    }
*/
}