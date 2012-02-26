/*
 * Copyright (c) 2009-2012. Joshua Tree Software, LLC.  All Rights Reserved.
 */

package com.jts.fortress.rbac;

import com.jts.fortress.*;
import com.jts.fortress.SecurityException;
import com.jts.fortress.arbac.AdminRole;
import com.jts.fortress.configuration.Config;
import com.jts.fortress.ldap.DaoUtil;
import com.jts.fortress.ldap.PoolMgr;
import com.jts.fortress.arbac.OrgUnit;
import com.jts.fortress.arbac.UserAdminRole;
import com.jts.fortress.constants.GlobalIds;
import com.jts.fortress.constants.GlobalErrIds;
import com.jts.fortress.pwpolicy.GlobalPwMsgIds;
import com.jts.fortress.pwpolicy.PwMessage;
import com.jts.fortress.pwpolicy.PwPolicyControl;
import com.jts.fortress.pwpolicy.openldap.OLPWControlImpl;
import com.jts.fortress.util.time.CUtil;
import com.jts.fortress.util.attr.AttrHelper;
import com.jts.fortress.util.attr.VUtil;

import org.apache.log4j.Logger;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPAttribute;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPAttributeSet;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPConnection;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPEntry;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPException;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPModification;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPModificationSet;
import com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPSearchResults;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Data access class for LDAP User entity.
 * <p/>
 * <p/>
 * The Fortress User LDAP schema follows:
 * <p/>
 * <h4>1. InetOrgPerson Structural Object Class </h4>
 * <code># The inetOrgPerson represents people who are associated with an</code><br />
 * <code># organization in some way.  It is a structural class and is derived</code><br />
 * <code># from the organizationalPerson which is defined in X.521 [X521].</code><br />
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 2.16.840.1.113730.3.2.2</code>
 * <li> <code>NAME 'inetOrgPerson'</code>
 * <li> <code>DESC 'RFC2798: Internet Organizational Person'</code>
 * <li> <code>SUP organizationalPerson</code>
 * <li> <code>STRUCTURAL</code>
 * <li> <code>MAY ( audio $ businessCategory $ carLicense $ departmentNumber $</code>
 * <li> <code>displayName $ employeeNumber $ employeeType $ givenName $</code>
 * <li> <code>homePhone $ homePostalAddress $ initials $ jpegPhoto $</code>
 * <li> <code>labeledURI $ mail $ manager $ mobile $ o $ pager $ photo $</code>
 * <li> <code>roomNumber $ secretary $ uid $ userCertificate $</code>
 * <li> <code>x500uniqueIdentifier $ preferredLanguage $</code>
 * <li> <code>userSMIMECertificate $ userPKCS12 ) )</code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>2. ftProperties AUXILIARY Object Class is used to store client specific name/value pairs on target entity</h4>
 * <code># This aux object class can be used to store custom attributes.</code><br />
 * <code># The properties collections consist of name/value pairs and are not constrainted by Fortress.</code><br />
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.2</code>
 * <li> <code>NAME 'ftProperties'</code>
 * <li> <code>DESC 'Fortress Properties AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY ( ftProps ) ) </code>
 * <li>  ------------------------------------------
 * </ul>
 * <p/>
 * <h4>3. ftUserAttrs is used to store user RBAC and Admin role assignment and other security attributes on User entity</h4>
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.1</code>
 * <li> <code>NAME 'ftUserAttrs'</code>
 * <li> <code>DESC 'Fortress User Attribute AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MUST ( ftId )</code>
 * <li> <code>MAY ( ftRC $ ftRA $ ftARC $ ftARA $ ftCstr</code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>4. ftMods AUXILIARY Object Class is used to store Fortress audit variables on target entity.</h4>
 * <ul>
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.4</code>
 * <li> <code>NAME 'ftMods'</code>
 * <li> <code>DESC 'Fortress Modifiers AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY (</code>
 * <li> <code>ftModifier $</code>
 * <li> <code>ftModCode $</code>
 * <li> <code>ftModId ) )</code>
 * <li>  ------------------------------------------
 * </ul>
 * <p/>

 *
 * @author smckinn
 * @created August 30, 2009
 */
public final class UserDAO
{
    /**
     * Don't let classes outside of this package construct this.
     */
    UserDAO()
    {
    }

    /**
     * @param entity
     * @return
     * @throws CreateException
     *
     */
    User create(User entity)
        throws CreateException
    {
        LDAPConnection ld = null;
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPAttributeSet attrs = new LDAPAttributeSet();
            attrs.add(DaoUtil.createAttributes(GlobalIds.OBJECT_CLASS, USER_OBJ_CLASS));

            entity.setInternalId();
            attrs.add(DaoUtil.createAttribute(GlobalIds.FT_IID, entity.getInternalId()));
            attrs.add(DaoUtil.createAttribute(GlobalIds.UID, entity.getUserId()));
            // CN is required on inetOrgPerson object class, if caller did not set, use the userId:
            if (!VUtil.isNotNullOrEmpty(entity.getCn()))
            {
                entity.setCn(entity.getUserId());
            }
            attrs.add(DaoUtil.createAttribute(GlobalIds.CN, entity.getCn()));
            // SN is required on inetOrgPerson object class, if caller did not set, use the userId:
            if (!VUtil.isNotNullOrEmpty(entity.getSn()))
            {
                entity.setSn(entity.getUserId());
            }
            attrs.add(DaoUtil.createAttribute(SN, entity.getSn()));
            attrs.add(DaoUtil.createAttribute(PW, new String(entity.getPassword())));
            attrs.add(DaoUtil.createAttribute(DISPLAY_NAME, entity.getCn()));

            // These are multi-valued attributes, use the util function to load:
            // These items are optional.  The utility function will return quietly if no items are loaded into collection:
            DaoUtil.loadAttrs(entity.getPhones(), attrs, TELEPHONE_NUMBER);
            DaoUtil.loadAttrs(entity.getMobiles(), attrs, MOBILE);
            DaoUtil.loadAttrs(entity.getEmails(), attrs, MAIL);

            // The following attributes are optional:
            if (VUtil.isNotNullOrEmpty(entity.getPwPolicy()))
            {
                String dn = GlobalIds.POLICY_NODE_TYPE + "=" + entity.getPwPolicy() + "," + Config.getProperty(GlobalIds.PPOLICY_ROOT);
                attrs.add(DaoUtil.createAttribute(OPENLDAP_POLICY_SUBENTRY, dn));
            }
            if (VUtil.isNotNullOrEmpty(entity.getOu()))
            {
                attrs.add(DaoUtil.createAttribute(GlobalIds.OU, entity.getOu()));
            }
            if (VUtil.isNotNullOrEmpty(entity.getDescription()))
            {
                attrs.add(DaoUtil.createAttribute(GlobalIds.DESC, entity.getDescription()));
            }
            // props are optional as well:
            // TODO: don't add "initial" property here.
            entity.addProperty("init", "");
            DaoUtil.loadProperties(entity.getProperties(), attrs, GlobalIds.PROPS);
            // map the userid to the name field in constraint:
            entity.setName(entity.getUserId());
            attrs.add(DaoUtil.createAttribute(GlobalIds.CONSTRAINT, CUtil.setConstraint(entity)));
            loadUserRoles(entity.getRoles(), attrs);
            loadUserAdminRoles(entity.getAdminRoles(), attrs);
            loadAddress(entity.getAddress(), attrs);
            String dn = GlobalIds.UID + "=" + entity.getUserId() + "," + Config.getProperty(GlobalIds.USER_ROOT);
            LDAPEntry myEntry = new LDAPEntry(dn, attrs);
            DaoUtil.add(ld, myEntry, entity);
            entity.setDn(dn);
        }
        catch (LDAPException e)
        {
            String error = CLS_NM + ".create userId [" + entity.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new CreateException(GlobalErrIds.USER_ADD_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return entity;
    }


    /**
     * @param entity
     * @return
     * @throws UpdateException
     */
    User update(User entity)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(entity.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            if (VUtil.isNotNullOrEmpty(entity.getCn()))
            {
                LDAPAttribute cn = new LDAPAttribute(GlobalIds.CN, entity.getCn());
                mods.add(LDAPModification.REPLACE, cn);
            }
            if (VUtil.isNotNullOrEmpty(entity.getSn()))
            {
                LDAPAttribute sn = new LDAPAttribute(SN, entity.getSn());
                mods.add(LDAPModification.REPLACE, sn);
            }
            if (VUtil.isNotNullOrEmpty(entity.getOu()))
            {
                LDAPAttribute ou = new LDAPAttribute(GlobalIds.OU, entity.getOu());
                mods.add(LDAPModification.REPLACE, ou);
            }
            if (VUtil.isNotNullOrEmpty(entity.getPassword()))
            {
                LDAPAttribute pw = new LDAPAttribute(PW, new String(entity.getPassword()));
                mods.add(LDAPModification.REPLACE, pw);
            }
            if (VUtil.isNotNullOrEmpty(entity.getDescription()))
            {
                LDAPAttribute desc = new LDAPAttribute(GlobalIds.DESC,
                    entity.getDescription());
                mods.add(LDAPModification.REPLACE, desc);
            }
            if (VUtil.isNotNullOrEmpty(entity.getPwPolicy()))
            {
                String szDn = GlobalIds.POLICY_NODE_TYPE + "=" + entity.getPwPolicy() + "," + Config.getProperty(GlobalIds.PPOLICY_ROOT);
                LDAPAttribute dn = new LDAPAttribute(OPENLDAP_POLICY_SUBENTRY, szDn);
                mods.add(LDAPModification.REPLACE, dn);
            }
            if (entity.isTemporalSet())
            {
                // map the userid to the name field in constraint:
                entity.setName(entity.getUserId());
                String szRawData = CUtil.setConstraint(entity);
                if (VUtil.isNotNullOrEmpty(szRawData))
                {
                    LDAPAttribute constraint = new LDAPAttribute(GlobalIds.CONSTRAINT, szRawData);
                    mods.add(LDAPModification.REPLACE, constraint);
                }
            }
            if (VUtil.isNotNullOrEmpty(entity.getRoles()))
            {
                loadUserRoles(entity.getRoles(), mods);
            }
            if (VUtil.isNotNullOrEmpty(entity.getAdminRoles()))
            {
                loadUserAdminRoles(entity.getAdminRoles(), mods);
            }
            if (VUtil.isNotNullOrEmpty(entity.getProperties()))
            {
                DaoUtil.loadProperties(entity.getProperties(), mods, GlobalIds.PROPS, true);
            }

            loadAddress(entity.getAddress(), mods);
            // These are multi-valued attributes, use the util function to load:
            DaoUtil.loadAttrs(entity.getPhones(), mods, TELEPHONE_NUMBER);
            DaoUtil.loadAttrs(entity.getMobiles(), mods, MOBILE);
            DaoUtil.loadAttrs(entity.getEmails(), mods, MAIL);

            if (mods.size() > 0)
            {
                DaoUtil.modify(ld, userDn, mods, entity);
                entity.setDn(userDn);
            }

            entity.setDn(userDn);
        }
        catch (LDAPException e)
        {
            String error = CLS_NM + ".update userId [" + entity.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_UPDATE_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return entity;
    }


    /**
     * @param entity
     * @param replace
     * @return
     * @throws UpdateException
     */
    User updateProps(User entity, boolean replace)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(entity.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            if (VUtil.isNotNullOrEmpty(entity.getProperties()))
            {
                DaoUtil.loadProperties(entity.getProperties(), mods, GlobalIds.PROPS, replace);
            }
            if (mods != null && mods.size() > 0)
            {
                DaoUtil.modify(ld, userDn, mods, entity);
                entity.setDn(userDn);
            }
            entity.setDn(userDn);
        }
        catch (LDAPException e)
        {
            String error = CLS_NM + ".updateProps userId [" + entity.getUserId() + "] isReplace [" + replace + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_UPDATE_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return entity;
    }


    /**
     * @param user
     * @throws RemoveException
     */
    String remove(User user)
        throws RemoveException
    {
        LDAPConnection ld = null;
        String userDn = getDn(user.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            DaoUtil.delete(ld, userDn, user);
        }
        catch (LDAPException e)
        {
            String error = CLS_NM + ".remove userId [" + user.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new RemoveException(GlobalErrIds.USER_DELETE_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param user
     * @throws com.jts.fortress.UpdateException
     *
     */
    void lock(User user)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(user.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            LDAPAttribute pwdAccoutLock = new LDAPAttribute(OPENLDAP_ACCOUNT_LOCKED_TIME, LOCK_VALUE);
            mods.add(LDAPModification.REPLACE, pwdAccoutLock);
            DaoUtil.modify(ld, userDn, mods, user);
        }
        catch (LDAPException e)
        {
            String error = CLS_NM + ".lock user [" + user.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_PW_LOCK_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
    }


    /**
     * @param user
     * @throws UpdateException
     *
     */
    void unlock(User user)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(user.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            LDAPAttribute pwdlockedTime = new LDAPAttribute(OPENLDAP_ACCOUNT_LOCKED_TIME);
            mods.add(LDAPModification.DELETE, pwdlockedTime);
            DaoUtil.modify(ld, userDn, mods, user);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.NO_SUCH_ATTRIBUTE)
            {
                log.info(CLS_NM + ".unlock user [" + user.getUserId() + "] no such attribute:" + OPENLDAP_ACCOUNT_LOCKED_TIME);
            }
            else
            {
                String error = CLS_NM + ".unlock user [" + user.getUserId() + "] caught LDAPException= " + e.getLDAPResultCode() + " msg=" + e.getMessage();
                throw new UpdateException(GlobalErrIds.USER_PW_UNLOCK_FAILED, error, e);
            }
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
    }


    /**
     * @param userId
     * @return
     * @throws com.jts.fortress.FinderException
     *
     */
    User getUser(String userId, boolean isRoles)
        throws FinderException
    {
        User entity = null;
        LDAPConnection ld = null;
        String userDn = getDn(userId);
        try
        {
            String[] uATTRS;
            // Retrieve role attributes?
            if (isRoles)
            {
                // Retrieve the User's assigned RBAC and Admin Role attributes from directory.
                uATTRS = DEFAULT_ATRS;

            }
            else
            {
                // Do not retrieve the User's assigned RBAC and Admin Role attributes from directory.
                uATTRS = AUTHN_ATRS;
            }

            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPEntry findEntry = DaoUtil.read(ld, userDn, uATTRS);
            entity = unloadLdapEntry(findEntry, 0);
            if (entity == null)
            {
                String warning = CLS_NM + ".getUser userId [" + userId + "] not found, Fortress errCode=" + GlobalErrIds.USER_NOT_FOUND;
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT)
            {
                String warning = CLS_NM + ".getUser COULD NOT FIND ENTRY for user [" + userId + "]";
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }

            String error = CLS_NM + ".getUser [" + userDn + "]= caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_READ_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return entity;
    }


    /**
     * @param userId
     * @return
     * @throws com.jts.fortress.FinderException
     */
    List<UserAdminRole> getUserAdminRoles(String userId)
        throws FinderException
    {
        List<UserAdminRole> roles = null;
        LDAPConnection ld = null;
        String userDn = getDn(userId);
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPEntry findEntry = DaoUtil.read(ld, userDn, AROLE_ATR);
            roles = unloadUserAdminRoles(findEntry, userId);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT)
            {
                String warning = CLS_NM + ".getUserAdminRoles COULD NOT FIND ENTRY for user [" + userId + "]";
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }

            String error = CLS_NM + ".getUserAdminRoles [" + userDn + "]= caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_READ_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return roles;
    }


    /**
     * @param userId
     * @return
     * @throws com.jts.fortress.FinderException
     *
     */
    List<String> getRoles(String userId)
        throws FinderException
    {
        List<String> roles = null;
        LDAPConnection ld = null;
        String userDn = getDn(userId);
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPEntry findEntry = DaoUtil.read(ld, userDn, ROLES);
            if (findEntry == null)
            {
                String warning = CLS_NM + ".getRoles userId [" + userId + "] not found, Fortress errCode=" + GlobalErrIds.USER_NOT_FOUND;
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }
            roles = DaoUtil.getAttributes(findEntry, GlobalIds.USER_ROLE_ASSIGN);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT)
            {
                String warning = CLS_NM + ".getRoles COULD NOT FIND ENTRY for user [" + userId + "]";
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }
            String error = CLS_NM + ".getRoles [" + userDn + "]= caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.URLE_SEARCH_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return roles;
    }


    /**
     * @param userId
     * @param password
     * @return
     * @throws com.jts.fortress.FinderException
     *
     * @throws com.jts.fortress.SecurityException
     */
    Session checkPassword(String userId, char[] password) throws FinderException
    {
        Session session = null;
        LDAPConnection ld = null;
        String userDn = getDn(userId);
        try
        {
            session = new ObjectFactory().createSession();
            session.setUserId(userId);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.USER);
            boolean result = PoolMgr.bind(ld, userDn, password);
            if (result)
            {
                // check openldap password policies here
                checkPwPolicies(ld, session);
                if (session.getErrorId() == 0)
                {
                    session.setAuthenticated(true);
                }
            }
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.INVALID_CREDENTIALS)
            {
                // Check controls to see if password is locked, expired or out of grace:
                checkPwPolicies(ld, session);
                // if check pw control did not find problem the user entered invalid pw:
                if (session.getErrorId() == 0)
                {
                    String info = "checkPassword INVALID PASSWORD for userId [" + userId + "]";
                    session.setMsg(info);
                    session.setErrorId(GlobalErrIds.USER_PW_INVLD);
                    session.setAuthenticated(false);
                }
            }
            else
            {
                String error = CLS_NM + ".checkPassword userId [" + userId + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
                throw new FinderException(GlobalErrIds.USER_READ_FAILED, error, e);
            }
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.USER);
        }
        return session;
    }


    /**
     * @param user
     * @return
     * @throws FinderException
     */
    List<User> findUsers(User user)
        throws FinderException
    {
        List<User> userList = new ArrayList<User>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {

            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter;
            if (VUtil.isNotNullOrEmpty(user.getUserId()))
            {
                // place a wild card after the input userId:
                String searchVal = VUtil.encodeSafeText(user.getUserId(), GlobalIds.USERID_LEN);
                filter = "(&(objectclass=" + objectClassImpl + ")("
                    + GlobalIds.UID + "=" + searchVal + "*))";
            }
            else if (VUtil.isNotNullOrEmpty(user.getInternalId()))
            {
                // internalUserId search
                String searchVal = VUtil.encodeSafeText(user.getInternalId(), GlobalIds.USERID_LEN);
                // this is not a wildcard search. Must be exact match.
                filter = "(&(objectclass=" + objectClassImpl + ")("
                    + GlobalIds.FT_IID + "=" + searchVal + "))";
            }
            else
            {
                // Beware - returns ALL users!!:"
                filter = "(objectclass=" + objectClassImpl + ")";
            }
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                if(userList.size() == 99)
                {
                    System.out.println("break");
                }
                userList.add(unloadLdapEntry(searchResults.next(), sequence++));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".findUsers caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param searchVal
     * @param limit
     * @return
     * @throws FinderException
     *
     */
    List<String> findUsers(String searchVal, int limit)
        throws FinderException
    {
        List<String> userList = new ArrayList<String>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            searchVal = VUtil.encodeSafeText(searchVal, GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + objectClassImpl + ")("
                + GlobalIds.UID + "=" + searchVal + "*))";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, USERID, false, GlobalIds.BATCH_SIZE, limit);
            while (searchResults.hasMoreElements())
            {
                LDAPEntry entry = searchResults.next();
                userList.add(DaoUtil.getAttribute(entry, GlobalIds.UID));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".findUsers caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param role
     * @return
     * @throws FinderException
     *
     */
    List<User> getAuthorizedUsers(Role role)
        throws FinderException
    {
        List<User> userList = new ArrayList<User>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String roleVal = VUtil.encodeSafeText(role.getName(), GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + USERS_AUX_OBJECT_CLASS_NAME + ")(";
            Set<String> roles = RoleUtil.getDescendants(role.getName());
            if (VUtil.isNotNullOrEmpty(roles))
            {
                filter += "|(" + GlobalIds.USER_ROLE_ASSIGN + "=" + roleVal + ")";
                for (String uRole : roles)
                {
                    filter += "(" + GlobalIds.USER_ROLE_ASSIGN + "=" + uRole + ")";
                }
                filter += ")";
            }
            else
            {
                filter += GlobalIds.USER_ROLE_ASSIGN + "=" + roleVal + ")";
            }
            filter += ")";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                userList.add(unloadLdapEntry(searchResults.next(), sequence++));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".getAuthorizedUsers role name [" + role.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.URLE_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param role
     * @return
     * @throws FinderException
     */
    List<User> getAssignedUsers(Role role)
        throws FinderException
    {
        List<User> userList = new ArrayList<User>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String roleVal = VUtil.encodeSafeText(role.getName(), GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + USERS_AUX_OBJECT_CLASS_NAME + ")("
                + GlobalIds.USER_ROLE_ASSIGN + "=" + roleVal + "))";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                userList.add(unloadLdapEntry(searchResults.next(), sequence++));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".getAssignedUsers role name [" + role.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.URLE_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     *
     * @param roles
     * @return
     * @throws FinderException
     */
    Set<String> getAssignedUsers(Set<String> roles)
        throws FinderException
    {
        Set<String> userSet = new TreeSet<String>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String filter = "(&(objectclass=" + USERS_AUX_OBJECT_CLASS_NAME + ")(|";
            if (VUtil.isNotNullOrEmpty(roles))
            {
                for (String roleVal : roles)
                {
                    String filteredVal = VUtil.encodeSafeText(roleVal, GlobalIds.USERID_LEN);
                    filter += "(" + GlobalIds.USER_ROLE_ASSIGN + "=" + filteredVal + ")";
                }
            }
            else
            {
                return null;
            }
            filter += "))";
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, USERID_ATRS, false, GlobalIds.BATCH_SIZE);
            while (searchResults.hasMoreElements())
            {
                userSet.add(DaoUtil.getAttribute(searchResults.next(), GlobalIds.UID));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".getAssignedUsers caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.URLE_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userSet;
    }


    /**
     * @param role
     * @return
     * @throws FinderException
     */
    List<User> getAssignedUsers(AdminRole role)
        throws FinderException
    {
        List<User> userList = new ArrayList<User>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String roleVal = VUtil.encodeSafeText(role.getName(), GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + USERS_AUX_OBJECT_CLASS_NAME + ")("
                + GlobalIds.USER_ADMINROLE_ASSIGN + "=" + roleVal + "))";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                userList.add(unloadLdapEntry(searchResults.next(), sequence++));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".getAssignedUsers admin role name [" + role.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.ARLE_USER_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param role
     * @param limit
     * @return
     * @throws FinderException
     *
     */
    List<String> getAuthorizedUsers(Role role, int limit)
        throws FinderException
    {
        List<String> userList = new ArrayList<String>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String roleVal = VUtil.encodeSafeText(role.getName(), GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + USERS_AUX_OBJECT_CLASS_NAME + ")("
                + GlobalIds.USER_ROLE_ASSIGN + "=" + roleVal + "))";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, USERID, false, GlobalIds.BATCH_SIZE, limit);
            while (searchResults.hasMoreElements())
            {
                LDAPEntry entry = searchResults.next();
                userList.add(DaoUtil.getAttribute(entry, GlobalIds.UID));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".getAuthorizedUsers role name [" + role.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.URLE_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param searchVal
     * @return
     * @throws FinderException
     */
    List<String> findUsersList(String searchVal)
        throws FinderException
    {
        List<String> userList = new ArrayList<String>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            searchVal = VUtil.encodeSafeText(searchVal, GlobalIds.USERID_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + objectClassImpl + ")("
                + GlobalIds.UID + "=" + searchVal + "*))";
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                userList.add((unloadLdapEntry(searchResults.next(), sequence++)).getUserId());
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".findUsersList caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param ou
     * @return
     * @throws FinderException
     */
    List<User> findUsers(OrgUnit ou, boolean limitSize)
        throws FinderException
    {
        List<User> userList = new ArrayList<User>();
        LDAPConnection ld = null;
        LDAPSearchResults searchResults;
        String userRoot = Config.getProperty(GlobalIds.USER_ROOT);
        try
        {
            String szOu = VUtil.encodeSafeText(ou.getName(), GlobalIds.OU_LEN);
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            String filter = "(&(objectclass=" + objectClassImpl + ")("
                + GlobalIds.OU + "=" + szOu + "))";
            int maxLimit;
            if (limitSize)
            {
                maxLimit = 10;
            }
            else
            {
                maxLimit = 0;
            }
            searchResults = DaoUtil.search(ld, userRoot,
                LDAPConnection.SCOPE_ONE, filter, DEFAULT_ATRS, false, GlobalIds.BATCH_SIZE, maxLimit);
            long sequence = 0;
            while (searchResults.hasMoreElements())
            {
                userList.add(unloadLdapEntry(searchResults.next(), sequence++));
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".findUsers caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_SEARCH_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userList;
    }


    /**
     * @param entity
     * @param newPassword
     * @return
     * @throws UpdateException
     *
     * @throws SecurityException
     */
    boolean changePassword(User entity, char[] newPassword)
        throws SecurityException
    {
        boolean rc = true;
        LDAPConnection ld = null;
        LDAPModificationSet mods;
        String userDn = getDn(entity.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.USER);
            PoolMgr.bind(ld, userDn, entity.getPassword());
            mods = new LDAPModificationSet();
            // TODO: fix this to allow user to update ftModifier attribute on record.  Currently getting LDAP 50 error - access control violation.
            // cannot do this - insufficient as user can't modify this attribute
            //DaoUtil.loadAdminData(entity, mods);
            LDAPAttribute pw = new LDAPAttribute(PW, new String(newPassword));
            mods.add(LDAPModification.REPLACE, pw);
            DaoUtil.modify(ld, userDn, mods);
        }
        catch (LDAPException e)
        {
            String warning = User.class.getName() + ".changePassword user [" + entity.getUserId() + "] ";
            if (e.getLDAPResultCode() == LDAPException.CONSTRAINT_VIOLATION)
            {
                warning += " constraint violation, ldap errCode=" + e.getLDAPResultCode() + " ldap msg=" + e.getMessage() + " Fortress errCode=" + GlobalErrIds.PSWD_CONST_VIOLATION;
                throw new PasswordException(GlobalErrIds.PSWD_CONST_VIOLATION, warning);
            }
            else if (e.getLDAPResultCode() == LDAPException.INSUFFICIENT_ACCESS_RIGHTS)
            {
                warning += " user not authorized to change password, ldap errCode=" + e.getLDAPResultCode() + " ldap msg=" + e.getMessage() + " Fortress errCode=" + GlobalErrIds.USER_PW_MOD_NOT_ALLOWED;
                throw new UpdateException(GlobalErrIds.USER_PW_MOD_NOT_ALLOWED, warning);
            }
            warning += " caught LDAPException errCode=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_PW_CHANGE_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.USER);
        }
        return rc;
    }


    /**
     * @param user
     * @throws UpdateException
     *
     */
    void resetUserPassword(User user)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(user.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            LDAPAttribute pw = new LDAPAttribute(PW, new String(user.getPassword()));
            mods.add(LDAPModification.REPLACE, pw);
            LDAPAttribute pwdReset = new LDAPAttribute(OPENLDAP_PW_RESET, "TRUE");
            mods.add(LDAPModification.REPLACE, pwdReset);
            DaoUtil.modify(ld, userDn, mods, user);
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".resetUserPassword userId [" + user.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_PW_RESET_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
    }


    /**
     * @param uRole
     * @return
     * @throws UpdateException
     *
     * @throws FinderException
     *
     */
    String assign(UserRole uRole)
        throws UpdateException, FinderException
    {
        LDAPConnection ld = null;
        String userDn = getDn(uRole.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            String szUserRole = uRole.getRawData();
            LDAPAttribute attr = new LDAPAttribute(GlobalIds.USER_ROLE_DATA, szUserRole);
            mods.add(LDAPModification.ADD, attr);
            attr = new LDAPAttribute(GlobalIds.USER_ROLE_ASSIGN, uRole.getName());
            mods.add(LDAPModification.ADD, attr);
            DaoUtil.modify(ld, userDn, mods, uRole);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.ATTRIBUTE_OR_VALUE_EXISTS)
            {
                String warning = CLS_NM + ".assign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] assignment already exists.";
                throw new FinderException(GlobalErrIds.URLE_ASSIGN_EXIST, warning);
            }
            else
            {
                String warning = CLS_NM + ".assign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
                throw new UpdateException(GlobalErrIds.URLE_ASSIGN_FAILED, warning, e);
            }
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param uRole
     * @return
     * @throws UpdateException
     *
     * @throws FinderException
     *
     */
    String deassign(UserRole uRole)
        throws UpdateException, FinderException
    {
        LDAPConnection ld = null;
        String userDn = getDn(uRole.getUserId());
        try
        {
            // read the user's RBAC role assignments to locate target record.  Need the raw data before attempting removal:
            List<UserRole> roles = getUserRoles(uRole.getUserId());
            int indx = -1;
            // Does the user have any roles assigned?
            if (roles != null)
            {
                // function call will set indx to -1 if name not found:
                indx = roles.indexOf(uRole);
                // Is the targeted name assigned to user?
                if (indx > -1)
                {
                    // Retrieve the targeted name:
                    UserRole fRole = roles.get(indx);
                    ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
                    // delete the name assignment attribute using the raw name data:
                    LDAPModificationSet mods = new LDAPModificationSet();
                    LDAPAttribute rAttr = new LDAPAttribute(GlobalIds.USER_ROLE_DATA, fRole.getRawData());
                    mods.add(LDAPModification.DELETE, rAttr);
                    rAttr = new LDAPAttribute(GlobalIds.USER_ROLE_ASSIGN, fRole.getName());
                    mods.add(LDAPModification.DELETE, rAttr);
                    DaoUtil.modify(ld, userDn, mods, uRole);
                }
            }
            // target name not found:
            if (indx == -1)
            {
                // The user does not have the target name assigned,
                String warning = CLS_NM + ".deassign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] assignment does not exist.";
                throw new FinderException(GlobalErrIds.URLE_ASSIGN_NOT_EXIST, warning);
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".deassign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.URLE_DEASSIGN_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param uRole
     * @return
     * @throws UpdateException
     *
     * @throws FinderException
     *
     */
    String assign(UserAdminRole uRole)
        throws UpdateException, FinderException
    {
        LDAPConnection ld = null;
        String userDn = getDn(uRole.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            String szUserRole = uRole.getRawData();
            LDAPAttribute attr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_DATA, szUserRole);
            mods.add(LDAPModification.ADD, attr);
            attr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_ASSIGN, uRole.getName());
            mods.add(LDAPModification.ADD, attr);
            DaoUtil.modify(ld, userDn, mods, uRole);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.ATTRIBUTE_OR_VALUE_EXISTS)
            {
                String warning = CLS_NM + ".assign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] assignment already exists.";
                throw new FinderException(GlobalErrIds.ARLE_ASSIGN_EXIST, warning);
            }
            else
            {
                String warning = CLS_NM + ".assign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
                throw new UpdateException(GlobalErrIds.ARLE_ASSIGN_FAILED, warning, e);
            }
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param uRole
     * @return
     * @throws UpdateException
     *
     * @throws FinderException
     *
     */
    String deassign(UserAdminRole uRole)
        throws UpdateException, FinderException
    {
        LDAPConnection ld = null;
        String userDn = getDn(uRole.getUserId());
        try
        {
            // read the user's ARBAC roles to locate record.  Need the raw data before attempting removal:
            List<UserAdminRole> roles = getUserAdminRoles(uRole.getUserId());
            //User user = getUser(uRole.getUserId(), true);
            //List<UserAdminRole> roles = user.getAdminRoles();
            int indx = -1;
            // Does the user have any roles assigned?
            if (roles != null)
            {
                // function call will set indx to -1 if name not found:
                indx = roles.indexOf(uRole);
                // Is the targeted name assigned to user?
                if (indx > -1)
                {
                    // Retrieve the targeted name:
                    UserRole fRole = roles.get(indx);
                    ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
                    // delete the name assignment attribute using the raw name data:
                    LDAPModificationSet mods = new LDAPModificationSet();
                    LDAPAttribute rAttr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_DATA, fRole.getRawData());
                    mods.add(LDAPModification.DELETE, rAttr);
                    rAttr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_ASSIGN, fRole.getName());
                    mods.add(LDAPModification.DELETE, rAttr);
                    DaoUtil.modify(ld, userDn, mods, uRole);
                }
            }
            // target name not found:
            if (indx == -1)
            {
                // The user does not have the target name assigned,
                String warning = CLS_NM + ".deassign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] assignment does not exist.";
                throw new FinderException(GlobalErrIds.ARLE_DEASSIGN_NOT_EXIST, warning);
            }
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".deassign userId [" + uRole.getUserId() + "] name [" + uRole.getName() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.ARLE_DEASSIGN_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param user
     * @return
     * @throws UpdateException
     *
     */
    String deletePwPolicy(User user)
        throws UpdateException
    {
        LDAPConnection ld = null;
        String userDn = getDn(user.getUserId());
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPModificationSet mods = new LDAPModificationSet();
            LDAPAttribute policy = new LDAPAttribute(OPENLDAP_POLICY_SUBENTRY);
            mods.add(LDAPModification.DELETE, policy);
            DaoUtil.modify(ld, userDn, mods, user);
        }
        catch (LDAPException e)
        {
            String warning = CLS_NM + ".deletePwPolicy userId [" + user.getUserId() + "] caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new UpdateException(GlobalErrIds.USER_PW_PLCY_DEL_FAILED, warning, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return userDn;
    }


    /**
     * @param le
     * @return
     * @throws LDAPException
     */
    private User unloadLdapEntry(LDAPEntry le, long sequence)
        throws LDAPException
    {
        User entity = new ObjectFactory().createUser();
        entity.setSequenceId(sequence);
        entity.setInternalId(DaoUtil.getAttribute(le, GlobalIds.FT_IID));
        entity.setDescription(DaoUtil.getAttribute(le, GlobalIds.DESC));
        entity.setUserId(DaoUtil.getAttribute(le, GlobalIds.UID));
        entity.setCn(DaoUtil.getAttribute(le, GlobalIds.CN));
        entity.setName(entity.getCn());
        entity.setSn(DaoUtil.getAttribute(le, SN));
        entity.setOu(DaoUtil.getAttribute(le, GlobalIds.OU));
        entity.setDn(le.getDN());
        DaoUtil.unloadTemporal(le, entity);
        entity.setRoles(unloadUserRoles(le, entity.getUserId()));
        entity.setAdminRoles(unloadUserAdminRoles(le, entity.getUserId()));
        entity.setAddress(unloadAddress(le));
        entity.setPhones(DaoUtil.getAttributes(le, TELEPHONE_NUMBER));
        entity.setMobiles(DaoUtil.getAttributes(le, MOBILE));
        entity.setEmails(DaoUtil.getAttributes(le, MAIL));
        String szBoolean = DaoUtil.getAttribute(le, OPENLDAP_PW_RESET);
        if (szBoolean != null && szBoolean.equalsIgnoreCase("true"))
        {
            entity.setReset(true);
        }
        szBoolean = DaoUtil.getAttribute(le, OPENLDAP_PW_LOCKED_TIME);
        if (szBoolean != null && szBoolean.equals(LOCK_VALUE))
        {
            entity.setLocked(true);
        }
        entity.addProperties(AttrHelper.getProperties(DaoUtil.getAttributes(le, GlobalIds.PROPS)));
        return entity;
    }


    /**
     * @param userId
     * @return
     * @throws FinderException
     */
    private List<UserRole> getUserRoles(String userId)
        throws FinderException
    {
        List<UserRole> roles = null;
        LDAPConnection ld = null;
        String userDn = getDn(userId);
        try
        {
            ld = PoolMgr.getConnection(PoolMgr.ConnType.ADMIN);
            LDAPEntry findEntry = DaoUtil.read(ld, userDn, ROLE_ATR);
            roles = unloadUserRoles(findEntry, userId);
        }
        catch (LDAPException e)
        {
            if (e.getLDAPResultCode() == LDAPException.NO_SUCH_OBJECT)
            {
                String warning = CLS_NM + ".getUserRoles COULD NOT FIND ENTRY for user [" + userId + "]";
                throw new FinderException(GlobalErrIds.USER_NOT_FOUND, warning);
            }

            String error = CLS_NM + ".getUserRoles [" + userDn + "]= caught LDAPException=" + e.getLDAPResultCode() + " msg=" + e.getMessage();
            throw new FinderException(GlobalErrIds.USER_READ_FAILED, error, e);
        }
        finally
        {
            PoolMgr.closeConnection(ld, PoolMgr.ConnType.ADMIN);
        }
        return roles;
    }

    /**
     * @param userId
     * @return
     */
    private String getDn(String userId)
    {
        return GlobalIds.UID + "=" + userId + "," + Config.getProperty(GlobalIds.USER_ROOT);
    }

    /**
     * @param ld
     * @param pwMsg
     */
    private void checkPwPolicies(LDAPConnection ld, PwMessage pwMsg)
    {
        int rc = 0;
        boolean success = false;
        String msgHdr = "checkPwPolicies for userId [" + pwMsg.getUserId() + "] ";
        if (ld != null)
        {
            if (!GlobalIds.OPENLDAP_IS_PW_POLICY_ENABLED)
            {
                pwMsg.setWarningId(GlobalPwMsgIds.NO_CONTROLS_FOUND);
                pwMsg.setErrorId(GlobalPwMsgIds.GOOD);
                String msg = msgHdr + "PW POLICY NOT ENABLED";
                pwMsg.setMsg(msg);
                log.debug(msg);
                return;
            }
            else if (pwControl != null)
            {
                pwControl.checkPasswordPolicy(ld, success, pwMsg);
            }
            // OpenLDAP has notified of password violation:
            if (pwMsg.getErrorId() > 0)
            {
                String errMsg;
                switch (pwMsg.getErrorId())
                {

                    case GlobalPwMsgIds.CHANGE_AFTER_RESET:
                        // Don't throw exception if authenticating in J2EE Realm - The Web application must give user a chance to modify their password.
                        if (!GlobalIds.IS_REALM)
                        {
                            errMsg = msgHdr + "PASSWORD HAS BEEN RESET BY LDAP_ADMIN_POOL_UID";
                            rc = GlobalErrIds.USER_PW_RESET;
                        }
                        else
                        {
                            errMsg = msgHdr + "PASSWORD HAS BEEN RESET BY LDAP_ADMIN_POOL_UID BUT ALLOWING TO CONTINUE DUE TO REALM";
                            success = true;
                            pwMsg.setWarningId(GlobalErrIds.USER_PW_RESET);
                        }
                        break;
                    case GlobalPwMsgIds.ACCOUNT_LOCKED:
                        errMsg = msgHdr + "ACCOUNT HAS BEEN LOCKED";
                        rc = GlobalErrIds.USER_PW_LOCKED;
                        break;
                    case GlobalPwMsgIds.PASSWORD_HAS_EXPIRED:
                        errMsg = msgHdr + "PASSWORD HAS EXPIRED";
                        rc = GlobalErrIds.USER_PW_EXPIRED;
                        break;
                    case GlobalPwMsgIds.NO_MODIFICATIONS:
                        errMsg = msgHdr + "PASSWORD MOD NOT ALLOWED";
                        rc = GlobalErrIds.USER_PW_MOD_NOT_ALLOWED;
                        break;
                    case GlobalPwMsgIds.MUST_SUPPLY_OLD:
                        errMsg = msgHdr + "MUST SUPPLY OLD PASSWORD";
                        rc = GlobalErrIds.USER_PW_MUST_SUPPLY_OLD;
                        break;
                    case GlobalPwMsgIds.INSUFFICIENT_QUALITY:
                        errMsg = msgHdr + "PASSWORD QUALITY VIOLATION";
                        rc = GlobalErrIds.USER_PW_NSF_QUALITY;
                        break;
                    case GlobalPwMsgIds.PASSWORD_TOO_SHORT:
                        errMsg = msgHdr + "PASSWORD TOO SHORT";
                        rc = GlobalErrIds.USER_PW_TOO_SHORT;
                        break;
                    case GlobalPwMsgIds.PASSWORD_TOO_YOUNG:
                        errMsg = msgHdr + "PASSWORD TOO YOUNG";
                        rc = GlobalErrIds.USER_PW_TOO_YOUNG;
                        break;
                    case GlobalPwMsgIds.HISTORY_VIOLATION:
                        errMsg = msgHdr + "PASSWORD IN HISTORY VIOLATION";
                        rc = GlobalErrIds.USER_PW_IN_HISTORY;
                        break;
                    default:
                        errMsg = msgHdr + "PASSWORD CHECK FAILED";
                        rc = GlobalErrIds.USER_PW_CHK_FAILED;
                        break;
                }
                pwMsg.setMsg(errMsg);
                pwMsg.setErrorId(rc);
                pwMsg.setAuthenticated(success);
                log.debug(errMsg);
            }
            else
            {
                // Checked out good:
                String msg = msgHdr + "PASSWORD CHECK SUCCESS";
                pwMsg.setMsg(msg);
                pwMsg.setErrorId(0);
                pwMsg.setAuthenticated(true);
                log.debug(msg);
            }
        }
        else
        {
            // Even though we didn't find valid pw control, the actual userid/pw check passed:
            pwMsg.setAuthenticated(success);
            pwMsg.setWarningId(GlobalPwMsgIds.NO_CONTROLS_FOUND);
            pwMsg.setErrorId(GlobalPwMsgIds.GOOD);
            String msg = msgHdr + "NO PASSWORD CONTROLS FOUND";
            pwMsg.setMsg(msg);
            log.warn(CLS_NM + ".checkPwPolicies " + msg);
        }
    }


    /**
     * Given a collection of ARBAC roles, {@link UserAdminRole}, convert to raw data format and load into ldap attribute set in preparation for ldap add.
     *
     * @param list  contains List of type {@link UserAdminRole} targeted for adding to ldap.
     * @param attrs collection of ldap attributes containing ARBAC role assignments in raw ldap format.
     */
    private static void loadUserAdminRoles(List<UserAdminRole> list, LDAPAttributeSet attrs)
    {
        if (list != null)
        {
            LDAPAttribute attr = null;
            LDAPAttribute attrNm = null;
            for (UserAdminRole userRole : list)
            {
                String szUserRole = userRole.getRawData();
                if (attr == null)
                {
                    attr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_DATA, szUserRole);
                    attrNm = new LDAPAttribute(GlobalIds.USER_ADMINROLE_ASSIGN, userRole.getName());
                }
                else
                {
                    attr.addValue(szUserRole);
                    attrNm.addValue(userRole.getName());
                }
            }
            if (attr != null)
            {
                attrs.add(attr);
                attrs.add(attrNm);
            }
        }
    }

    /**
     * Given a collection of RBAC roles, {@link UserRole}, convert to raw data format and load into ldap modification set in preparation for ldap modify.
     *
     * @param list contains List of type {@link UserRole} targeted for updating into ldap.
     * @param mods contains ldap modification set containing RBAC role assignments in raw ldap format to be updated.
     */
    private static void loadUserRoles(List<UserRole> list, LDAPModificationSet mods)
    {
        LDAPAttribute attr = null;
        LDAPAttribute attrNm = null;
        if (list != null)
        {
            for (UserRole userRole : list)
            {
                String szUserRole = userRole.getRawData();
                if (attr == null)
                {
                    attr = new LDAPAttribute(GlobalIds.USER_ROLE_DATA, szUserRole);
                    attrNm = new LDAPAttribute(GlobalIds.USER_ROLE_ASSIGN, userRole.getName());
                }
                else
                {
                    attr.addValue(szUserRole);
                }
            }
            if (attr != null)
            {
                mods.add(LDAPModification.REPLACE, attr);
                mods.add(LDAPModification.REPLACE, attrNm);
            }
        }
    }

    /**
     * Given a collection of ARBAC roles, {@link UserAdminRole}, convert to raw data format and load into ldap modification set in preparation for ldap modify.
     *
     * @param list contains List of type {@link UserAdminRole} targeted for updating to ldap.
     * @param mods contains ldap modification set containing ARBAC role assignments in raw ldap format to be updated.
     */
    private static void loadUserAdminRoles(List<UserAdminRole> list, LDAPModificationSet mods)
    {
        LDAPAttribute attr = null;
        LDAPAttribute attrNm = null;
        if (list != null)
        {
            for (UserAdminRole userRole : list)
            {
                String szUserRole = userRole.getRawData();
                if (attr == null)
                {
                    attr = new LDAPAttribute(GlobalIds.USER_ADMINROLE_DATA, szUserRole);
                    attrNm = new LDAPAttribute(GlobalIds.USER_ADMINROLE_ASSIGN, userRole.getName());
                }
                else
                {
                    attr.addValue(szUserRole);
                }
            }
            if (attr != null)
            {
                mods.add(LDAPModification.REPLACE, attr);
                mods.add(LDAPModification.REPLACE, attrNm);
            }
        }
    }

    /**
     * Given a collection of RBAC roles, {@link UserRole}, convert to raw data format and load into ldap attribute set in preparation for ldap add.
     *
     * @param list  contains List of type {@link UserRole} targeted for adding to ldap.
     * @param attrs collection of ldap attributes containing RBAC role assignments in raw ldap format.
     */
    private static void loadUserRoles(List<UserRole> list, LDAPAttributeSet attrs)
    {
        if (list != null)
        {
            LDAPAttribute attr = null;
            LDAPAttribute attrNm = null;
            for (UserRole userRole : list)
            {
                String szUserRole = userRole.getRawData();
                if (attr == null)
                {
                    attr = new LDAPAttribute(GlobalIds.USER_ROLE_DATA, szUserRole);
                    attrNm = new LDAPAttribute(GlobalIds.USER_ROLE_ASSIGN, userRole.getName());
                }
                else
                {
                    attr.addValue(szUserRole);
                    attrNm.addValue(userRole.getName());
                }
            }
            if (attr != null)
            {
                attrs.add(attr);
                attrs.add(attrNm);
            }
        }
    }

    /**
     * Given a User address, {@link Address}, load into ldap attribute set in preparation for ldap add.
     *
     * @param address  contains User address {@link Address} targeted for adding to ldap.
     * @param attrs collection of ldap attributes containing RBAC role assignments in raw ldap format.
     */
    private static void loadAddress(Address address, LDAPAttributeSet attrs)
    {
        if (address != null)
        {
            LDAPAttribute attr;
            if(VUtil.isNotNullOrEmpty(address.getAddresses()))
            {
                for(String val : address.getAddresses())
                {
                    attr = new LDAPAttribute(POSTAL_ADDRESS, val);
                    attrs.add(attr);
                }
            }

            if(VUtil.isNotNullOrEmpty(address.getCity()))
            {
                attr = new LDAPAttribute(L, address.getCity());
                attrs.add(attr);
            }
            //if(VUtil.isNotNullOrEmpty(address.getCountry()))
            //{
            //    attr = new LDAPAttribute(GlobalIds.COUNTRY, address.getAddress1());
            //    attrs.add(attr);
            //}
            if(VUtil.isNotNullOrEmpty(address.getPostalCode()))
            {
                attr = new LDAPAttribute(POSTAL_CODE, address.getPostalCode());
                attrs.add(attr);
            }
            if(VUtil.isNotNullOrEmpty(address.getPostOfficeBox()))
            {
                attr = new LDAPAttribute(POST_OFFICE_BOX, address.getPostOfficeBox());
                attrs.add(attr);
            }
            if(VUtil.isNotNullOrEmpty(address.getState()))
            {
                attr = new LDAPAttribute(STATE, address.getState());
                attrs.add(attr);
            }
        }
    }

    /**
     * Given an address, {@link Address}, load into ldap modification set in preparation for ldap modify.
     *
     * @param address contains entity of type {@link Address} targeted for updating into ldap.
     * @param mods contains ldap modification set contains attributes to be updated in ldap.
     */
    private static void loadAddress(Address address, LDAPModificationSet mods)
    {
        LDAPAttribute attr;
        if (address != null)
        {
            if(VUtil.isNotNullOrEmpty(address.getAddresses()))
            {
                for(String val : address.getAddresses())
                {
                    attr = new LDAPAttribute(POSTAL_ADDRESS, val);
                    mods.add(LDAPModification.REPLACE, attr);
                }
            }
            if(VUtil.isNotNullOrEmpty(address.getCity()))
            {
                attr = new LDAPAttribute(L, address.getCity());
                mods.add(LDAPModification.REPLACE, attr);
            }
            if(VUtil.isNotNullOrEmpty(address.getPostalCode()))
            {
                attr = new LDAPAttribute(POSTAL_CODE, address.getPostalCode());
                mods.add(LDAPModification.REPLACE, attr);
            }
            if(VUtil.isNotNullOrEmpty(address.getPostOfficeBox()))
            {
                attr = new LDAPAttribute(POST_OFFICE_BOX, address.getPostOfficeBox());
                mods.add(LDAPModification.REPLACE, attr);
            }
            if(VUtil.isNotNullOrEmpty(address.getState()))
            {
                attr = new LDAPAttribute(STATE, address.getState());
                mods.add(LDAPModification.REPLACE, attr);
            }
        }
    }

    /**
     * Given an ldap entry containing organzationalPerson address information, convert to {@link Address}
     *
     * @param le     contains ldap entry to retrieve admin roles from.
     * @return entity of type {@link Address}.
     * @throws com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPException in the event of ldap client error.
     */
    private static Address unloadAddress(LDAPEntry le)
        throws LDAPException
    {
        Address addr = new ObjectFactory().createAddress();
        List<String> pAddrs = DaoUtil.getAttributes(le, POSTAL_ADDRESS);
        if (pAddrs != null)
        {
            for (String pAddr : pAddrs)
            {
                addr.setAddress(pAddr);
            }
        }
        addr.setCity(DaoUtil.getAttribute(le, L));
        addr.setState(DaoUtil.getAttribute(le, STATE));
        addr.setPostalCode(DaoUtil.getAttribute(le, POSTAL_CODE));
        addr.setPostOfficeBox(DaoUtil.getAttribute(le, POST_OFFICE_BOX));

        // todo: fixme:
        //addr.setCountry(DaoUtil.getAttribute(le, GlobalIds.COUNTRY));

        return addr;
    }

    /**
     * Given an ldap entry containing ARBAC roles assigned to user, retrieve the raw data and convert to a collection of {@link UserAdminRole}
     * including {@link com.jts.fortress.util.time.Constraint}.
     *
     * @param le     contains ldap entry to retrieve admin roles from.
     * @param userId attribute maps to {@link UserAdminRole#userId}.
     * @return List of type {@link UserAdminRole} containing admin roles assigned to a particular user.
     * @throws com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPException in the event of ldap client error.
     */
    private static List<UserAdminRole> unloadUserAdminRoles(LDAPEntry le, String userId)
        throws LDAPException
    {
        List<UserAdminRole> uRoles = null;
        List<String> roles = DaoUtil.getAttributes(le, GlobalIds.USER_ADMINROLE_DATA);
        if (roles != null)
        {
            long sequence = 0;
            uRoles = new ArrayList<UserAdminRole>();
            for (String raw : roles)
            {
                UserAdminRole ure = new ObjectFactory().createUserAdminRole();
                ure.load(raw);
                ure.setSequenceId(sequence++);
                ure.setUserId(userId);
                uRoles.add(ure);
            }
        }
        return uRoles;
    }

    /**
     * Given an ldap entry containing RBAC roles assigned to user, retrieve the raw data and convert to a collection of {@link UserRole}
     * including {@link com.jts.fortress.util.time.Constraint}.
     *
     * @param le     contains ldap entry to retrieve roles from.
     * @param userId attribute maps to {@link UserRole#userId}.
     * @return List of type {@link UserRole} containing RBAC roles assigned to a particular user.
     * @throws com.unboundid.ldap.sdk.migrate.ldapjdk.LDAPException in the event of ldap client error.
     */
    private static List<UserRole> unloadUserRoles(LDAPEntry le, String userId)
        throws LDAPException
    {
        List<UserRole> uRoles = null;
        List<String> roles = DaoUtil.getAttributes(le, GlobalIds.USER_ROLE_DATA);
        if (roles != null)
        {
            long sequence = 0;
            uRoles = new ArrayList<UserRole>();
            for (String raw : roles)
            {
                UserRole ure = new ObjectFactory().createUserRole();
                ure.load(raw);
                ure.setUserId(userId);
                ure.setSequenceId(sequence++);
                uRoles.add(ure);
            }
        }
        return uRoles;
    }

    private static final String CLS_NM = UserDAO.class.getName();
    private static final Logger log = Logger.getLogger(CLS_NM);
    private static PwPolicyControl pwControl;

    /**
     * Initialize the OpenLDAP Pw Policy validator.
     */
    static
    {
        if (GlobalIds.OPENLDAP_IS_PW_POLICY_ENABLED)
        {
            pwControl = new OLPWControlImpl();
        }
    }

    /*
      *  *************************************************************************
      *  **  OpenAccessMgr USERS STATICS
      *  ************************************************************************
      */
    private final static String USERS_AUX_OBJECT_CLASS_NAME = "ftUserAttrs";
    private final static String ORGANIZATIONAL_PERSON_OBJECT_CLASS_NAME = "organizationalPerson";
    private final static String USER_OBJECT_CLASS = "user.objectclass";
    private final static String USER_OBJ_CLASS[] = {
       GlobalIds.TOP, Config.getProperty(USER_OBJECT_CLASS), USERS_AUX_OBJECT_CLASS_NAME, GlobalIds.PROPS_AUX_OBJECT_CLASS_NAME, GlobalIds.FT_MODIFIER_AUX_OBJECT_CLASS_NAME
    };
    private final static String objectClassImpl = Config.getProperty(USER_OBJECT_CLASS);
    private final static String SN = "sn";
    private final static String PW = "userpassword";
    /**
     * Constant contains the locale attribute name used within organizationalPerson ldap object classes.
     */
    private final static String L = "l";

    /**
     * Constant contains the postal address attribute name used within organizationalPerson ldap object classes.
     */
    private final static String POSTAL_ADDRESS = "postalAddress";

    /**
     * Constant contains the state attribute name used within organizationalPerson ldap object classes.
     */
    private final static String STATE = "st";

    /**
     * Constant contains the postal code attribute name used within organizationalPerson ldap object classes.
     */
    private final static String POSTAL_CODE = "postalCode";

    /**
     * Constant contains the post office box attribute name used within organizationalPerson ldap object classes.
     */
    private final static String POST_OFFICE_BOX = "postOfficeBox";


    /**
     * Constant contains the country attribute name used within organizationalPerson ldap object classes.
     */
    private final static String COUNTRY = "c";

    /**
     * Constant contains the mobile attribute values used within iNetOrgPerson ldap object classes.
     */
    private final static String MOBILE = "mobile";

    /**
     * Constant contains the telephone attribute values used within organizationalPerson ldap object classes.
     */
    private final static String TELEPHONE_NUMBER = "telephoneNumber";

    /**
     * Constant contains the email attribute values used within iNetOrgPerson ldap object classes.
     */
    private final static String MAIL = "mail";



    private final static String DISPLAY_NAME = "displayName";
    private final static String OPENLDAP_POLICY_SUBENTRY = "pwdPolicySubentry";
    private final static String OPENLDAP_PW_RESET = "pwdReset";
    private final static String OPENLDAP_PW_LOCKED_TIME = "pwdAccountLockedTime";
    private final static String OPENLDAP_ACCOUNT_LOCKED_TIME = "pwdAccountLockedTime";

    final private static String LOCK_VALUE = "000001010000Z";

    private final static String[] USERID = {GlobalIds.UID};
    private final static String[] ROLES = {GlobalIds.USER_ROLE_ASSIGN};

    private final static String[] USERID_ATRS = {
        GlobalIds.UID
    };

    private final static String[] AUTHN_ATRS = {
        GlobalIds.FT_IID, GlobalIds.UID, PW, GlobalIds.DESC, GlobalIds.OU, GlobalIds.CN, SN,
        GlobalIds.CONSTRAINT, OPENLDAP_PW_RESET, OPENLDAP_PW_LOCKED_TIME, GlobalIds.PROPS
    };
    private final static String[] DEFAULT_ATRS = {
        GlobalIds.FT_IID, GlobalIds.UID, PW, GlobalIds.DESC, GlobalIds.OU, GlobalIds.CN, SN,
        GlobalIds.USER_ROLE_DATA, GlobalIds.CONSTRAINT, GlobalIds.USER_ROLE_ASSIGN, OPENLDAP_PW_RESET,
        OPENLDAP_PW_LOCKED_TIME, GlobalIds.PROPS, GlobalIds.USER_ADMINROLE_ASSIGN, GlobalIds.USER_ADMINROLE_DATA,
        POSTAL_ADDRESS, L, POSTAL_CODE, POST_OFFICE_BOX, STATE, TELEPHONE_NUMBER, MOBILE, MAIL,
    };

    private final static String[] ROLE_ATR = {
        GlobalIds.USER_ROLE_DATA
    };

    private final static String[] AROLE_ATR = {
        GlobalIds.USER_ADMINROLE_DATA
    };
}