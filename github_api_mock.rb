require 'active_support/all'
require 'addressable/uri'
require 'fileutils'
require 'haml'
require 'json'
require 'pry'
require 'rest-client'
require 'sinatra'
require 'yaml'

CLIENT_ID = 'the GitHub OAuth client_id'.freeze
CLIENT_SECRET = 'the GitHub OAuth client_secret'.freeze

USERS = {
  adam: {
    login: 'adam',
    id: 1,
    email: 'adam.admin@bogus.com',
    access_token: 'the-access-token-for-adam',
    name: 'Adam Admin',
    emails: [
      { email: 'adam.admin@example.com',
        verified: true }
    ]
  },
  normin: {
    login: 'normin',
    id: 2,
    email: 'normin.normalo@bogus.com',
    name: 'Normin Normalo',
    access_token: 'the-access-token-for-normin',
    emails: [
    ]
  },
  silvan: {
    login: 'silvan',
    id: 3,
    email: 'silvan.stranger@bogus.com',
    name: 'Silvan Strange',
    access_token: 'the-access-token-for-silvan',
    emails: [
    ]
  },
  tessa: {
    login: 'tessa',
    id: 4,
    email: 'tessa.team@bogus.com',
    name: 'Tessa Team',
    access_token: 'the-access-token-for-tessa',
    emails: [
    ]
  } }.with_indifferent_access

ORGS = {
  "TestOrg": [:normin]
}.freeze

USER = USERS[(ENV['GITHUB_MOCK_USER'].presence || 'adam')]

CALLBACK_URL = 'http://localhost:' \
   << (ENV['UI2_HTTP_PORT'].presence || '8883') \
   << '/cider-ci/ui2/session/oauth/github/sign-in'

def find_user_by_access_token(access_token)
  USERS.find { |k, v| v['access_token'] == access_token }.try(:second)
end

### auth ######################################################################

def header_token
  env['HTTP_AUTHORIZATION'].match(/Bearer\s+(\S.*)/)[1] rescue nil
end

### intialize #################################################################

def initialize
  super()
  @hooks = []
end


### Meta ######################################################################

get '/status' do
  'OK'
end


### Repositories ##############################################################

post '/repos/:owner/:repo/statuses/:sha' do

  sleep 1

  if header_token != 'test-token'
    status 403
  else
    FileUtils.mkdir_p 'tmp'
    data = params.merge('auth_token' => header_token,
                        'body' => JSON.parse(request.body.read))
    IO.write 'tmp/last-status-post.yml', data.to_yaml
    if File.exist? 'tmp/last-status-post.yml'
      logger.info "written tmp/last-status-post.yml with content: #{data.to_json}"
    else
      logger.warn "NOT WRITTEN tmp/last-status-post.yml with content: #{data.to_json}"
    end

    content_type 'application/json'
    status 201
    '{}'
  end

end


### Oauth #####################################################################

get '/login/oauth/authorize' do
  halt(422, 'No such client') unless params[:client_id] == CLIENT_ID

  html = USERS.map do |k, v|
    Haml::Engine.new(
      <<-HAML.strip_heredoc
      %form{method: 'POST'}
        %input{type: 'hidden', name: 'login', value: '#{v[:login]}'}
        %button{type: 'submit'}
          Sign in as #{v[:login]}
      %hr
      HAML
    ).render
  end.join("\n")

  html
end

post '/login/oauth/authorize' do
  uri = Addressable::URI.parse(CALLBACK_URL)
  uri.query_values = { state: params[:state], code: params[:login] }
  redirect(uri.to_s, 303)
end

post '/login/oauth/access_token' do
  halt(403, 'CODE missmatch') unless USERS[params[:code]]
  halt(403, 'CLIENT_ID missmatch') unless params[:client_id] == CLIENT_ID
  halt(403, 'CLIENT_SECRET missmatch') unless params[:client_secret] == CLIENT_SECRET
  content_type 'application/json'
  { access_token: USERS[params[:code]]['access_token'] }.to_json
end


# User ######################################################################

get '/user' do
  unless user = find_user_by_access_token( header_token )
    halt(404, '')
  else
    content_type 'application/json'
    user.slice(:login, :id, :email).to_json
  end
end

get '/user/emails' do
  unless user = find_user_by_access_token( header_token )
    halt(404, '')
  else
    content_type 'application/json'
    user[:emails].to_json
  end
end

get '/users/:user' do
  unless user = USERS[params[:user]]
    halt(404, 'User not found')
    unless user['access_token'] == header_token
      halt(403, 'Wrong Access Token')
    else
      content_type 'application/json'
      user.slice(:login, :id, :email).to_json
    end
  end
end

###############################################################################
### Org  ######################################################################
###############################################################################

get '/orgs/TestOrg/members/normin' do
  halt(204, '')
end


### push notifications / aka webhooks #########################################

get '/repos/:owner/:repo/hooks' do
  content_type 'application/json'
  @hooks.to_json
end

post '/repos/:owner/:repo/hooks' do
  if header_token != 'test-token'
    status 403
  else
    id = rand(10000)
    hooks_url = "http://localhost:#{ENV['GITHUB_API_MOCK_PORT']}/repos/#{params[:owner]}/#{params[:repo]}/hooks/#{id}"
    defaults = {
      id: id,
      url: hooks_url,
      test_url: "#{hooks_url}/test",
      ping_url: "#{hooks_url}/pings",
      events: ["push"],
      created_at: Time.now,
      updated_at: Time.now,
    }.with_indifferent_access
    body = JSON.parse(request.body.read).with_indifferent_access
    hook = defaults.deep_merge(body)
    @hooks << hook
    content_type 'application/json'
    status 201
    hook.to_json
  end
end

post '/repos/:owner/:repo/hooks/:id/test' do
  if repo = @hooks.select{|r| r[:id].to_s == params[:id]}.first
    url = repo[:config][:url]
    RestClient.post url, {}.to_json, {content_type: :json, accept: :json}
    status 204
  else
    status 404
  end
end


### Team ######################################################################

get '/orgs/:org/teams' do
  content_type 'application/json'
  if params[:org] == 'TestOrg'
    [{ id: 'admin_team_id',
       name: 'Admins' }].to_json
  else
    [].to_json
  end
end

get '/teams/:id/memberships/:username' do
  content_type 'application/json'
  if params[:id] == 'admin_team_id'
    if params[:username] == 'tessa'
      { role: 'member',
        state: 'active'
      }.to_json
    else
      halt(404, '{}')
    end
  else
    halt(404, '{}')
  end
end


### demo project git ##########################################################

Dir.chdir("../demo-project-bash") do
  system("git repack -a") || raise("`git repack -a ` failed")
  system("git update-server-info") || raise("`git update-server-info` failed")
  puts "GIT-SERVER: repacked and server-info updated"
end

get '/git/demo-project-bash/*' do
  auth = Rack::Auth::Basic::Request.new(request.env)
  path = "../demo-project-bash/.git/#{params[:splat].join("/")}"
  unless auth.provided?
    headers['WWW-Authenticate'] = 'Basic realm="Restricted Area"'
    halt 401, "Not authorized\n"
  else
    unless auth.credentials.first == "the-github-token"
      logger.warn "GIT-SERVER: auth token is missing or wrong #{auth.credentials}"
      halt 404, "Not found\n"
    else
      if File.exists? path
        send_file path
      else
      logger.info "GIT-SERVER: file really not found"
        halt 404, "Not found\n"
      end
    end
  end
end
