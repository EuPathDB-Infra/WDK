import React from 'react';
import PropTypes from 'prop-types';
import UserIdentity from './UserIdentity';
import UserPassword from './UserPassword';
import UserContact from './UserContact';
import ApplicationSpecificProperties from './ApplicationSpecificProperties';
import { wrappable } from '../utils/componentUtils';

/** The user attribute that points to application specific properties */
const APPLICATION_SPECIFIC_PROPERTIES = "applicationSpecificProperties";

/**
 * This React component provides the form wrapper and enclosed fieldsets for the user profile/account form.
 * @param props
 * @returns {XML}
 * @constructor
 */
const UserAccountForm = (props) => {
  let { user, onTextChange, onEmailChange, onFormStateChange, disableSubmit, saveProfile } = props;

  return(
    <form className="wdk-UserProfile-profileForm" name="userProfileForm" onSubmit={saveProfile} >
      <p><i className="fa fa-asterisk"></i> = required</p>
      <UserIdentity user={user} onEmailChange={onEmailChange} onTextChange={onTextChange} />
      <br />
      <UserPassword user={user} wdkConfig={props.wdkConfig} />
      <br />
      <UserContact user={user} onTextChange={onTextChange} />
      <br />
      <ApplicationSpecificProperties user={user} onFormStateChange={onFormStateChange} name={APPLICATION_SPECIFIC_PROPERTIES} />
      <div>
        <input type="submit" value="Save" disabled={disableSubmit} />
      </div>
    </form>
  );
};

UserAccountForm.propTypes = {

  /** The user object to be modified */
  user: PropTypes.object.isRequired,

  /** The on change handler for email text box inputs */
  onEmailChange:  PropTypes.func.isRequired,

  /** The on change handler for text box inputs */
  onTextChange: PropTypes.func.isRequired,

  /** Indicates that submit button should be enabled/disabled */
  disableSubmit:  PropTypes.bool.isRequired,

  /** The on submit handler for the form */
  saveProfile:  PropTypes.func.isRequired,
  
  /** WDK config for setting correct change password link */
  wdkConfig:  PropTypes.object.isRequired
};

export default wrappable(UserAccountForm);
