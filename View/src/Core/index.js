import 'babel-polyfill';
import 'Core/vendor';

import * as Components from 'Components';
import * as FilterServiceUtils from 'Components/AttributeFilter/Utils/FilterServiceUtils';

import * as ReporterUtils from 'Views/ReporterForm/reporterUtils';

import WdkService from 'Utils/WdkService';
import * as WdkModel from 'Utils/WdkModel';
import * as Platform from 'Utils/Platform';
import * as TreeUtils from 'Utils/TreeUtils';
import * as PromiseUtils from 'Utils/PromiseUtils';
import * as CategoryUtils from 'Utils/CategoryUtils';
import * as OntologyUtils from 'Utils/OntologyUtils';
import * as FormSubmitter from 'Utils/FormSubmitter';
import * as IterableUtils from 'Utils/IterableUtils';
import * as ComponentUtils from 'Utils/ComponentUtils';
import * as StaticDataUtils from 'Utils/StaticDataUtils';

import * as Stores from 'Core/State/Stores';
import * as Controllers from 'Core/Controllers';
import * as ActionCreators from 'Core/ActionCreators';
import { initialize, wrapComponents } from 'Core/main';

__webpack_public_path__ = window.__asset_path_remove_me_please__; // eslint-disable-line

export {
  Stores,
  WdkModel,
  Platform,
  TreeUtils,
  WdkService,
  initialize,
  Components,
  Controllers,
  PromiseUtils,
  CategoryUtils,
  OntologyUtils,
  ReporterUtils,
  FormSubmitter,
  IterableUtils,
  wrapComponents,
  ComponentUtils,
  ActionCreators,
  StaticDataUtils,
  FilterServiceUtils
};
