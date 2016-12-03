module Helpers
  module Global
    extend self

    def wait_until(wait_time = 60, &block)
      begin
        Timeout.timeout(wait_time) do
          until value = block.call
            sleep(0.2)
          end
          value
        end
      rescue Timeout::Error => e
        fail Timeout::Error.new(block.source)
      end
    end


    def click_on_first(locator, options = {})
      wait_until(3){first(:link_or_button, locator, options)}
      first(:link_or_button, locator, options).click
    end

  end
end
