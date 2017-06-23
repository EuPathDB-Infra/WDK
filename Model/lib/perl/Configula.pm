package WDK::Model::Configula;

=head1 NAME

WDK::Model::Configula - core methods for configula script

=head1 DESCRIPTION

This module is for use by the EuPathDB team to aid internal development.
It is not supported.

It takes advantage of EuPathDB directory naming conventions to bootstrap
several configuration values so that they can be used to generate a
metaConfig yaml file to be fed to a WDK configuration script such as
eupathSiteConfigure or templateSiteConfigure

=cut

use strict;
use warnings FATAL => qw( all );
use File::Basename;
use Cwd qw(realpath);
use DBI;
use XML::Twig;
use XML::Simple;
use Getopt::Long;
use YAML qw(LoadFile);

our $scriptname = basename ( (caller(0))[1] );

my %dblinkMap = (
    # USE LOWERCASE KEYS!
    'apicomms'    => 'prods.login_comment',
    'apicommn'    => 'prodn.login_comment',
    'apicommdevs' => 'devs.login_comment',
    'apicommdevn' => 'devn.login_comment',
    'amazcomms'   => 'amazS.login_comment',
    'amazcommn'   => 'amazN.login_comment',
    'icmrcomm'    => 'icemr.login_comment',
    'gus4commn'   => 'gus4commN.login_comment',
    'gus4comms'   => 'gus4commS.login_comment',
    'gus4commt'   => 'gus4commT.login_comment',
    'mbiocommn'   => 'mbiocommN.login_comment',
    'mbiocomms'   => 'mbiocommS.login_comment',
    'prsmcomm'    => 'prism.login_comment',
    'prsmcomms'   => 'prisms.login_comment',
    'prsmcommn'   => 'prismn.login_comment',
    'rm15873'     => 'rm15873.login_comment',
    'rm25199s'    => 'rm25199s.login_comment',
    'rm25199n'    => 'rm25199n.login_comment',
    'rm9972'      => 'rm9972.login_comment',
    'userdb'      => 'vm.userdb', # standalone virtual machine
);

=head1 METHODS
=cut

=head2 new

  use WDK::Model::Configula;
  my $wmc = WDK::Model::Configula->new();

=cut
sub new {
    my ($class, $opts) = @_;

    my $self = {};
    bless $self;

    my (
      $appDb_login,
      $appDb_database,
      $userDb_database,
      $target_site,
      $g_use_map,
      $g_skip_db_test,
    ) = get_cli_args();

    $self->{'user_has_specified_values'} = defined ($appDb_login || $appDb_database || $userDb_database);
    $self->{'g_use_map'} = $g_use_map || undef;

    my $web_base_dir = '/var/www';
    my $site_symlink = "$web_base_dir/$target_site";
    my $product = dirname(readlink $site_symlink);
    my $project_home = realpath("$site_symlink/project_home");
    my $gus_home = realpath("$site_symlink/gus_home");
    my $site_etc = realpath("$site_symlink/etc");
    my $wdk_config_dir = "$gus_home/config";
    my $wdk_product_config_dir = "$gus_home/config/$product";

    my $webapp = basename(readlink $site_symlink);
    my ($webapp_nover) = $webapp =~ m/(^[a-zA-Z_]+)/;

    my $userDb_login = 'uga_fed';

    $self->{'g_skip_db_test'} = $g_skip_db_test;
    $self->{'target_site'} = $target_site;
    $self->{'appDb_login'} = $appDb_login;
    $self->{'userDb_login'} = $userDb_login;
    $self->{'appDb_database'} = $appDb_database || undef;
    $self->{'userDb_database'} = $userDb_database || undef;
    $self->{'euparc'} = $self->find_euparc();
    $self->{'map_file'} = "$site_etc/master_configuration_set";
    $self->{'meta_config_file'} = "${site_etc}/metaConfig_${scriptname}";
    $self->{'webapp'} = $webapp;
    $self->{'webapp_nover'} = $webapp_nover;
    $self->{'web_base_dir'} = $web_base_dir;
    $self->{'site_symlink'} = $site_symlink;
    $self->{'product'} = $product;
    $self->{'project_home'} = $project_home;
    $self->{'gus_home'} = $gus_home;
    $self->{'site_etc'} = $site_etc;
    $self->{'wdk_config_dir'} = $wdk_config_dir;
    $self->{'wdk_product_config_dir'} = $wdk_product_config_dir;
    $self->{'common_webservices_dir'} = (-e "/var/www/Common/prodSiteFilesMirror") ?
                                        "Common/prodSiteFilesMirror/webServices" :
                                        "Common/devSiteFilesMirror/webServices";
    $self->{'webservice_files_mirror'} = "$self->{'web_base_dir'}/$self->{'common_webservices_dir'}";
    # $self->{'rls_webservice_data_dir'} = "$self->{'webservice_files_mirror'}/$self->{'product'}/build-$self->{'build_number'}";
    $self->{'server_hostname'} = qx(/bin/hostname);
    $self->{'host_class'} = $self->host_class($self->{'target_site'});
    $self->{'host_class_prefix'} = $self->{'host_class'} ? $self->{'host_class'} . '.' : '';
    $self->{'canonical_hostname'} = $self->canonical_hostname($self->{'target_site'});

    if ($self->{'g_use_map'}) {
        open(my $fh, $self->{'map_file'}) or die $!;
        my @hits = grep /^$self->{'target_site'}/, <$fh>;
        die "Did not find an entry for $self->{'target_site'} in $self->{'map_file'}\n" unless $hits[0];
        ($self->{'site'}, $self->{'appDb_database'},  $self->{'userDb_database'}, $self->{'appDb_login'},  $self->{'userDb_login'}) = split(/\s+/, $hits[0]);
        print "<$scriptname> using '$self->{'appDb_login'}\@$self->{'appDb_database'}',  '$self->{'userDb_login'}\@$self->{'userDb_database'}'\n";
        if ( ! ($self->{'appDb_login'} && $self->{'appDb_database'} && $self->{'userDb_database'}) ) {
            die "$self->{'map_file'} does not have sufficient data for $self->{'target_site'}. Quitting with no changes made.\n";
        }
    }

    $self->{'userDbLink'} = $self->dblink($self->{'userDb_database'}, $self->{'appDb_database'});

    $self->{'appDb_password'} = $self->std_password($self->{'euparc'}, $self->{'appDb_login'}, $self->{'appDb_database'});

    if ( ! $self->{'appDb_password'} ) {
      die "Did not find password for $self->{'appDb_login'} in $self->{'euparc'} . Quitting with no changes made.\n";
    }

    $self->{'userDb_login'} = $self->{'userDb_login'} || 'uga_fed';
    $self->{'userDb_password'} = $self->std_password($self->{'euparc'}, $self->{'userDb_login'}, $self->{'userDb_database'});;

    if ( ! $self->{'userDb_password'} ) {
      die "Did not find password for $self->{'userDb_login'} in $self->{'euparc'} . Quitting with no changes made.\n";
    }

    # webapp_nover is always valid thanks to apache redirects, and
    # is especially desired for production sites
    $self->{'webServiceUrl'} = 'http://' . $self->{'target_site'} . '/' . $self->{'webapp_nover'} . '/services/WsfService';

    $self->{'google_analytics_id'} = $self->google_analytics_id($self->{'euparc'}, $self->{'canonical_hostname'});

    # MicrobiobiomeDB needs user_db; https://redmine.apidb.org/issues/23526
    if ( $self->{'product'} eq 'MicrobiomeDB' || $self->{'product'} eq 'ClinEpiDB' ) {
      $self->{'authenticationMethod'} = 'user_db'; # oauth2 or user_db
    } else {
      $self->{'authenticationMethod'} = 'oauth2'; # oauth2 or user_db
    }

    $self->{'oauthUrl'} = 'https://eupathdb.org/oauth';
    $self->{'oauthClientId'} = 'apiComponentSite';
    $self->{'oauthClientSecret'} = $self->oauth_secret($self->{'euparc'}, $self->{'oauthClientId'});

    $self->sanity_check();

    return $self;
}


####################################################################
# pre-flight sanity checks
####################################################################
sub sanity_check {
    my ($self) = @_;
    if ( $self->{'g_use_map'} && $self->{'user_has_specified_values'}) {
        die "can not set specific values when using --usemap\n";;
    }

    if ($self->{'g_use_map'}) {
        die "--usemap chosen but $self->{'map_file'} not found\n" unless ( -r $self->{'map_file'});
    }

    die "\nFATAL: I do not know what dblink to use for '" . lc $self->{'userDb_database'} . "'\n" .
      "  I know about: " . join(', ', keys(%dblinkMap)) . "\n\n"
      if ( (lc($self->{userDb_database}) ne lc($self->{appDb_database})) && (! $self->{'userDbLink'} || $self->{'userDbLink'} eq '@') );
      # OK to have no dblink if both user and and app schemas are in the same database

    if ( ! $self->{'g_skip_db_test'}) {
      $self->testDbConnection($self->{'appDb_login'}, $self->{'appDb_password'}, $self->{'appDb_database'});
      $self->testDbConnection($self->{'userDb_login'}, $self->{'userDb_password'}, $self->{'userDb_database'});
    }
}

=head2 do_configure

  run the supported configuration script

  Args:
  short_cmd: short name for the supported configuration script in $GUS_HOME/bin - eupathSiteConfigure, templateSiteConfigure
  product: model name, aka. project name, aka. product name. e.g. ToxoDB, CryptoDB, TemplateDB
  path to metaConfig file: This module will write one to $wmc->{'meta_config_file'} .

  $wmc->do_configure('templateSiteConfigure', TemplateDB, $wmc->{'meta_config_file'});

=cut
sub do_configure {
  my ($self, $short_cmd, $product, $meta_config_file) = @_;

  my $cmd = "$self->{'gus_home'}/bin/$short_cmd -model $product -filename $meta_config_file";
  print "<$scriptname> Attempting: '$cmd' ...";
  system("$cmd");
  if ($? == 0) {
    print "<$scriptname> OK\n";
  } elsif ($? == -1) {
    print "\n<$scriptname> FATAL: $!\n";
    exit -1;
  } else {
    print "\n<$scriptname> FATAL: $short_cmd exited with status " . ($? >> 8) . "\n";
    exit ($? >> 8);
  };
}

sub get_cli_args {
  my ($self) = @_;

  my (
    $appDb_login,
    $appDb_database,
    $userDb_database,
    $target_site,
    $g_use_map,
    $g_skip_db_test,
  );

  {
    local $SIG{__WARN__} = sub {
      my $message = shift;
      die "<$scriptname> FATAL: " . $message;
  };

  my $optRslt = GetOptions(
        "alogin=s"   => \$appDb_login,
        "adb=s"      => \$appDb_database,
        "udb=s"      => \$userDb_database,
        "usemap"     => \$g_use_map,  # get config data from a master file from gus_home/config
        "skipdbtest" => \$g_skip_db_test, # for when you know this will fail, or know it will succeed!
      );
  }

  $target_site = lc $ARGV[0];

  return (
    $appDb_login,
    $appDb_database,
    $userDb_database,
    $target_site,
    $g_use_map,
    $g_skip_db_test,
  );
}

=head2 dblink

  Return dblink for given apicomm;

  $wmc->dblink('apicommS')

=cut
sub dblink {
    my ($self, $userDb, $appDb) = @_;

    # no dblink if both user and and app schemas are in the same database
    if (defined $appDb && lc($userDb) eq lc($appDb)) {
      return undef;
    }

    return  $dblinkMap{lc($userDb)};
}

# retreive password from users ~/.euparc
sub std_password {
  my ($self, $euparc, $login, $database) = @_;

  ($login, $database) = map{ lc } ($login, $database);

  my $rc = XMLin($euparc,
      ForceArray => ['user'],
      ForceContent => 1,
      KeyAttr => [ user => "login"],
      ContentKey => '-content',
  );

  return $rc->{database}->{$database}->{user}->{$login}->{password} ||
      $rc->{database}->{user}->{$login}->{password};
}


# retreive google analytics id from users ~/.euparc
sub google_analytics_id {
  my ($self, $euparc, $site) = @_;

  ($site) = map{ lc } ($site);

  my $rc = XMLin($euparc,
    KeyAttr => [ site => 'hostname'],
  );

  return $rc->{'sites'}->{'site'}->{$site}->{'google_analytics_id'};
}

# retreive OAuth client secret from user's ~/.euparc
sub oauth_secret {
  my ($self, $euparc, $clientid) = @_;

  ($clientid) = map{ lc } ($clientid);

  my $rc = XMLin($euparc,
    ForceArray => ['client'],
    ForceContent => 1,
    KeyAttr => [ user => "clientId"],
  );

  return $rc->{'oauth'}->{'client'}->{$clientid}->{'clientSecret'};
}

# return 'class' of host, e.g. qa, beta, integrate or hostname.
# return empty string if no hostname (e.g. toxodb.org)
# This is not always the hostname. A site with 'q1' hostname is a 'qa' class.
sub host_class {
  my ($self, $target_site) = @_;
  my ($host_class) = $target_site =~ m/^([^\.]+)\.[^\.]+\..+/;
  return '' unless $host_class;
  $host_class = 'qa'    if $host_class =~ m/^q\d/;
  $host_class = 'alpha' if $host_class =~ m/^a\d/;
  $host_class = 'beta'  if $host_class =~ m/^b\d/;
  $host_class = 'prism' if $host_class =~ m/^pr\d/;
  $host_class = ''      if $host_class =~ m/^w\d/;
  return $host_class;
}

# given foo.goodb.org, return goodb.org
sub base_domain {
  my ($self, $target_site) = @_;
  my ($base_domain) = $target_site =~ m/^(?:[^\.]+\.)?([^\.]+\..+)/;
  return $base_domain;
}

# convert, for example
#  q1.toxodb.org to qa.toxodb.org
#  w1.toxodb.org to toxodb.org
#  integrate.gus4.toxodb.org to integrate.gus4.toxodb.org (no conversion)
sub canonical_hostname {
  my ($self, $target_site) = @_;
  return  $self->{'host_class_prefix'} . $self->base_domain($target_site);
}

sub webapp_domain_map {
  my ($self) = @_;
  return {
      'amoeba'    => 'amoebadb.org',
      'cryptodb'  => 'cryptodb.org',
      'eupathdb'  => 'eupathdb.org',
      'fungidb'   => 'fungidb.org',
      'giardiadb' => 'giardiadb.org',
      'mbio'      => 'microbiomedb.org',
      'micro'     => 'microsporidiadb.org',
      'piro'      => 'piroplasmadb.org',
      'plasmo'    => 'plasmodb.org',
      'schisto'   => 'schistodb.net',
      'toxo'      => 'toxodb.org',
      'trichdb'   => 'trichdb.org',
      'tritrypdb' => 'tritrypdb.org',
  };
}

sub domain_from_webapp {
  my ($self, $webapp) = @_;
  my ($webapp_nover) =  $webapp =~ m/(^[a-zA-Z_]+)/;
  my $map = webapp_domain_map();
  return $map->{$webapp_nover};
}

sub webapp_from_domain {
  my ($self, $domain) = @_;
  my $map = webapp_domain_map();
  my %revmap = reverse %$map;
  return $revmap{lc $domain};
}

sub testDbConnection {
  my ($self, $login, $password, $db) = @_;
  my $dbh = DBI->connect("dbi:Oracle:$db", $login, $password, {
        PrintError =>0,
        RaiseError =>0
      }) or warn "\n<$scriptname> WARN: Can't connect to $db with $login: $DBI::errstr\n\n";;
  $dbh->disconnect if $dbh;
}

sub find_euparc  {
  my ($self) = @_;
  # ibuilder shell sets HOME to be the website and
  # REAL_HOME to that of joeuser
  if ( defined $ENV{REAL_HOME} && -r "$ENV{REAL_HOME}/.euparc") {
      return "$ENV{REAL_HOME}/.euparc";
  } elsif ( -r "$ENV{HOME}/.euparc") {
      return "$ENV{HOME}/.euparc";
  }
  die "Required .euparc file not found\n";
}

=head2 validate_meta_config

  Compare metaConfig.yaml.sample with given metaconfig file,
  checking for missing or extra properties, indicating that
  either the sample or this script needs to be updated.

  Args:
  sample: path to metaConfig.yaml.sample
  meta_config: path to metaConfig file;  This module writes one to $wmc->{'meta_config_file'}

=cut
sub validate_meta_config {
  my ($self, $sample, $meta_config) = @_;
  my @required_not_found;
  my @extra_found;

  print "\n<$scriptname>  Validating the required properties in\n $meta_config\nagainst\n $sample\n\n";

  my ($sample_settings) = LoadFile($sample);
  my $meta_settings = LoadFile($meta_config);

  for my $property (keys %{$sample_settings->{'required'}}) {
    if ( ! defined $meta_settings->{'required'}->{$property} ) {
      push @required_not_found, $property;
    }
  }

  for my $property (keys %{$meta_settings->{'required'}}) {
    if ( ! defined $sample_settings->{'required'}->{$property} ) {
      push @extra_found, $property;
    }
  }

  if (scalar(@required_not_found) > 0 || scalar(@extra_found) > 0) {
    if (scalar(@required_not_found) > 0) {
      print "\n";
      print "<$scriptname> Fatal: Required properties not found:\n";
      print join("\n", @required_not_found);
    }
    print "\n";
    if (scalar(@extra_found) > 0) {
      print "\n";
      print "<$scriptname> Fatal: Found properties not required:\n";
      print join("\n", @extra_found);
    }
    print "\n";
    print "Resolve this in either\n";
    print " $sample\n";
    print "or the meta config file generation in the '$scriptname' script.\n";
    exit 1;
  }

}

=head2 write_meta_config

  Write a metaConfig yaml file to $self->{'meta_config_file'}

  Args
  content: yaml content for the metaConfig file

=cut
sub write_meta_config {
  my ($self, $content) = @_;
  open(MC, ">$self->{'meta_config_file'}") or die "could not open $self->{'meta_config_file'} for writing.\n";
  print MC $content;
  close MC;
}

1;
