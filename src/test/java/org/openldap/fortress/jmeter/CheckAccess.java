/*
 * This work is part of OpenLDAP Software <http://www.openldap.org/>.
 *
 * Copyright 1998-2014 The OpenLDAP Foundation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted only as authorized by the OpenLDAP
 * Public License.
 *
 * A copy of this license is available in the file LICENSE in the
 * top-level directory of the distribution or, alternatively, at
 * <http://www.OpenLDAP.org/license.html>.
 */

package org.openldap.fortress.jmeter;

import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.LoggerFactory;
import org.openldap.fortress.AccelMgr;
import org.openldap.fortress.AccelMgrFactory;
import org.openldap.fortress.AccessMgr;
import org.openldap.fortress.AccessMgrFactory;
import org.openldap.fortress.rbac.Permission;
import org.openldap.fortress.rbac.Session;
import org.openldap.fortress.rbac.TestUtils;
import org.openldap.fortress.rbac.User;
import org.openldap.fortress.util.attr.VUtil;

import static org.junit.Assert.*;

/**
 * Description of the Class
 *
 * @author Shawn McKinney
 */
public class CheckAccess extends AbstractJavaSamplerClient
{
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger( CheckAccess.class );
    private static int count = 0;
    private Session session;
    private AccelMgr accelMgr;
    private AccessMgr accessMgr;
/*
    private int key = getKey();
    private int ctr = 0;
    private String userId = "loadtestuser" + key;
*/
    private int key = 0;
    private int ctr = 0;
    private String userId = "";
    private boolean isFortress = false;

    /**
     * Description of the Method
     *
     * @param samplerContext Description of the Parameter
     * @return Description of the Return Value
     */
    public SampleResult runTest( JavaSamplerContext samplerContext )
    {
        SampleResult sampleResult = new SampleResult();
        try
        {
            int opCount = ++ctr % 10;
            if( opCount == 0 )
                opCount = 10;

            int objCount = (ctr / 10) + 1;
            if(objCount > 10)
            {
                objCount = objCount % 10;
            }
            if(objCount == 0)
            {
                objCount = 1;
            }

            //int objCount = ((ctr / 10) + 1) % 10;
            String opName = "oper" + opCount;
            String objName = "loadtestobject" + objCount;
            sampleResult.sampleStart();
            String message;
            if(isFortress)
            {
                message = "FT ";
            }
            else
            {
                message = "AC ";
            }


            message += "CheckAccess userId: " + userId + ", objName:" + objName + ", opName: " + opName;
/*
            LOG.info( message );
            System.out.println( message );
*/

            assertNotNull( session );
            assertTrue( session.isAuthenticated() );
            Permission perm = new Permission();
            perm.setObjName( objName );
            perm.setOpName( opName );
            boolean result = false;
            if(isFortress)
            {
                assertNotNull( accessMgr );
                result = accessMgr.checkAccess( session, perm );
            }
            else
            {
                assertNotNull( accelMgr );
                result = accelMgr.checkAccess( session, perm );
            }

            // positive test case:
            assertTrue( message, result );
            sampleResult.sampleEnd();
            sampleResult.setBytes(1);
            sampleResult.setResponseMessage("test checkAccess completed");
            sampleResult.setSuccessful(true);
        }
        catch ( org.openldap.fortress.SecurityException se )
        {
            String error = "ThreadId:" + getThreadId() + "Error running test: " + se;
            LOG.error( "ThreadId:" + getThreadId() + "Error running test: " + se );
            se.printStackTrace();
            fail( error );
            sampleResult.setSuccessful( false );
        }

        return sampleResult;
    }

    /**
     *
     * @return
     */
    synchronized private int getKey( )
    {
        return ++count;
    }


    private String getThreadId()
    {
        return "" + Thread.currentThread().getId();
    }

    /**
     * Description of the Method
     *
     * @param samplerContext Description of the Parameter
     */
    public void setupTest( JavaSamplerContext samplerContext )
    {
        ctr = 0;
        if(!VUtil.isNotNullOrEmpty( userId ))
        {
            key = getKey();
            userId = "loadtestuser" + key;
        }
        try
        {
            String val = samplerContext.getParameter( "type" );
            System.out.println("PARAMETER VALUE = " + val);
            if(session == null)
            {
                String message;
                User user = new User(userId);
                // positive test case:
                user.setPassword( "secret".toCharArray() );
                if( VUtil.isNotNullOrEmpty( val ) && val.equals( "1" ))
                {
                    message = "FT SETUP CreateSession, User: " + user.getUserId() + ", key: " + key + ", TID: " + getThreadId();
                    isFortress = true;
                    accessMgr = AccessMgrFactory.createInstance( TestUtils.getContext() );
                    session = accessMgr.createSession( user, false );
                }
                else
                {
                    message = "AC SETUP CreateSession, User: " + user.getUserId() + ", key: " + key + ", TID: " + getThreadId();
                    isFortress = false;
                    accelMgr = AccelMgrFactory.createInstance( TestUtils.getContext() );
                    session = accelMgr.createSession( user, false );
                }
                LOG.info( message );
                System.out.println( message );
            }

            assertNotNull( session );
            assertTrue( session.isAuthenticated() );
        }
        catch ( org.openldap.fortress.SecurityException se )
        {
            String error = "setupTest ThreadId:" + getThreadId() + " Error starting test: " + se;
            LOG.error( "ThreadId:" + getThreadId() + "Error running test: " + se );
            se.printStackTrace();
            fail(error);
        }
    }

    /**
     * Description of the Method
     *
     * @param samplerContext Description of the Parameter
     */
    public void teardownTest( JavaSamplerContext samplerContext )
    {
        try
        {
            String message = "TEARDOWN UserId:" + userId + ", key: " + key + ", TID: " + getThreadId();
            LOG.info( message );
            System.out.println( message );
            if(!isFortress)
            {
                assertNotNull( session );
                accelMgr.deleteSession( session );
                //Thread.sleep( 100 );
            }
            session = null;
        }
        catch ( org.openldap.fortress.SecurityException se )
        {
            String error = "teardownTest ThreadId:" + getThreadId() + " Error stopping test: " + se;
            LOG.error( error );
            se.printStackTrace();
        }
/*
        catch ( InterruptedException ie )
        {
            // ignore
        }
*/
    }
}