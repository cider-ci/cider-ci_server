require 'spec_helper'
require 'open3'

module DemoProject
  class << self

    def demo_project_system_path 
      Pathname(
        "#{Dir.pwd}/demo-project/"
      ).cleanpath.to_s
    end

    def exec! cmd
      Dir.chdir demo_project_system_path do
        wrappec_cmd = <<-CMD.strip_heredoc
          #!/bin/env/bash
          set -euxo
        #{cmd}
        CMD
        Open3.popen3(wrappec_cmd) do |stdin, stdout, stderr, wait_thr|
          exit_status = wait_thr.value
          unless exit_status.success?
            abort stderr.read
          else
            stdout.read.strip
          end
        end
      end
    end

    def push_commit id, api_token_secret
      exec! <<-CMD.strip_heredoc
        git push --force http://#{api_token_secret}@localhost:#{port}/projects/demo-project.git #{id}:master
      CMD
    end
  end
end

shared_context :demo_project do

  let :demo_project_system_path do
    Pathname(
      "#{Dir.pwd}/demo-project/"
    ).cleanpath.to_s
  end

  before do
    visit '/'
    click_on_first 'Projects'
    click_on_first 'Add project'
    fill_in 'name', with: 'Demo-Project'
    click_on_first 'Add project'
    wait_until do 
      page.has_content? 'Project "Demo-Project"'
    end
    visit '/'
    click_on_first 'Commits'
    DemoProject.push_commit 'HEAD', @api_token_secret
  end
end

