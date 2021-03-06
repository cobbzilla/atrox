#
# Cookbook Name:: histori
# Recipe:: default
#

apache2 = Chef::Recipe::Apache2
java = Chef::Recipe::Java

run_as=node[:histori][:run_as]
current=node[:histori][:current]
histori_bag = data_bag_item('histori', 'init')
env = histori_bag['environment']

%w( redis-server ).each do |pkg|
  package pkg do
    action :install
  end
end

user run_as do
  comment 'Runs the histori-api'
  home '/home/histori'
  shell '/bin/bash'
  system true
end

[ '/home/histori', '/home/histori/current', current, "#{current}/target", "#{current}/scripts", "#{current}/logs" ].each do |dir|
  directory dir do
    owner run_as
    group run_as
    mode '0755'
    action :create
  end
end

cookbook_file "#{current}/target/histori-server.jar" do
  source 'assets/histori-server.jar'
  owner run_as
  group run_as
  mode '0755'
  action :create
end

%w( run.sh scripts/ppsql scripts/dropalldb scripts/pg_dumpall scripts/pg_restoreall scripts/flushredis ).each do |script|
  cookbook_file "#{current}/#{script}" do
    source "assets/#{script}"
    owner run_as
    group run_as
    mode '0755'
    action :create
  end
end

%w(site email-templates legal).each do |archive|
  cookbook_file "#{current}/#{archive}.tar.gz" do
    source "assets/histori-#{archive}.tar.gz"
    owner run_as
    group run_as
    mode '0755'
    action :create
  end
  bash "unpack #{archive}" do
    user run_as
    cwd current
    code <<-EOH
tar xf #{archive}.tar.gz && rm #{archive}.tar.gz
    EOH
  end
end

env['DEPLOY_ENV'] = 'default'
env['ASSETS_DIR'] = "#{current}/site"
env['EMAIL_TEMPLATE_ROOT'] = "#{current}/email"
env['LEGAL_INFO'] = "#{current}/legal"

server_name = URI.parse(env['PUBLIC_BASE_URI']).host.downcase

template '/etc/apache2/sites-available/histori.conf' do
  mode '0744'
  variables ({
      :server_name => server_name,
      :cert_name => 'ssl-https',
      :doc_root => env['ASSETS_DIR'],
      :api_port => env['HISTORI_SERVER_PORT']
  })
  action :create
end

bash 'write ~/.histori.env' do
  user run_as
  cwd current
  environment env
  code <<-EOH
env | grep -v '^_' \
    | grep -v '^USER='     | grep -v '^USERNAME=' \
    | grep -v '^MAIL='     | grep -v '^PATH=' \
    | grep -v '^SHELL='    | grep -v '^TERM='      | grep -v '^SHLVL=' \
    | grep -v '^LC_ALL='   | grep -v '^LS_COLORS=' | grep -v '^TERMCAP=' \
    | grep -v '^STY='      | grep -v '^PWD='       | grep -v '^LANG=' \
    | grep -v '^HOME='     | grep -v '^LOGNAME='   | grep -v '^WINDOW=' \
    | grep -v '^LESSOPEN=' | grep -v '^LESSCLOSE=' | grep -v '^SUDO' \
    | sed -e 's/^/export /' | sed -e "s/=/='/" | sed -e "s/$/'/" | sed -e "s/''/'/g" \
    > ~histori/.histori.env
  EOH
end

%w(ssl proxy proxy_http headers include substitute expires).each do |mod|
  apache2.enable_module self, mod
end

apache2.enable_site self, 'histori'
apache2.reload self
