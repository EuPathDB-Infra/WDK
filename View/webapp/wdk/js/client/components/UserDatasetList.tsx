import React from 'react';
import { withRouter, InjectedRouter } from 'react-router';
import { wrappable } from '../utils/componentUtils';
import { UserDataset } from '../utils/WdkModel';
import { bytesToHuman } from '../utils/Converters';
import DataTable from './DataTable';
import Loading from './Loading';

type Props = {
  userDatasets: UserDataset[],
  router: InjectedRouter
};

const getRowId = (row: { id: number }) => row.id;

const columns = [
  { name: 'id', displayName: 'ID' },
  { name: 'name', displayName: 'Name' },
  { name: 'summary', displayName: 'Summary' },
  { name: 'type', displayName: 'Type' },
  { name: 'installed', displayName: 'Installed' },
  { name: 'owner', displayName: 'Owner' },
  { name: 'shared', displayName: 'Shared With' },
  { name: 'created', displayName: 'Created' },
  { name: 'modified', displayName: 'Modified' },
  { name: 'size', displayName: 'Size' },
  { name: 'quota', displayName: 'Quota Usage %' }
];

const mapUserDatasets = (userDatasets: UserDataset[], router: InjectedRouter) =>
  userDatasets.length === 0 ? userDatasets : userDatasets.map(ud => ({
    id: `<a href="${router.createHref(`/workspace/datasets/${ud.id}`)}">${ud.id}</a>`,
    name: ud.meta.name,
    summary: ud.meta.summary,
    type: `${ud.type.name} ${ud.type.version}`,
    installed: ud.isInstalled.toString(),
    owner: ud.owner,
    shared: ud.sharedWith.map(share => share.userDisplayName).join(', '),
    created: new Date(ud.created).toLocaleDateString(),
    modified: new Date(ud.modified).toLocaleDateString(),
    size: bytesToHuman(ud.size),
    quota: `${ud.percentQuotaUsed}%`
  }));

const UserDatasetList = (props: Props) =>
  <div>
    <h1>User Data Sets</h1>
    <DataTable columns={columns}
               data={mapUserDatasets(props.userDatasets, props.router)}
               getRowId={getRowId} />
  </div>

export default wrappable(withRouter(UserDatasetList));
