require 'spec_helper'

shared_context :demo_project do
  let :demo_project_system_path do
    Pathname(
      "#{Dir.pwd}/demo-project/"
    ).cleanpath.to_s
  end
end

