require 'test/unit'

class TS_BasicStorage < Test::Unit::TestCase
  def setup
    @server = StorageServer.new
  end
  
  def teardown
    @server.stop
  end
  
  def test_get_put
    (0..10).each do |i|
      @server.put("getput", Record.new(:key => "key#{i}", :value => "value#{i}"))
    end
    
    (0..10).each do |i|
      assert_equal("value#{i}", @server.get("getput", "key#{i}"))
    end
  end
  
  def test_update_value
    (0..10).each do |i| # set initial values
      @server.put("updateval", Record.new(:key => "key#{i}", :value => "value#{i}"))
    end
    
    (0..10).each do |i| # make sure intial values are there
      assert_equal("value#{i}", @server.get("updateval", "key#{i}"))
    end
    
    (0..10).each do |i| # change intial values
      @server.put("updateval", Record.new(:key => "key#{i}", :value => "value#{i*2}"))
    end
    
   (0..10).each do |i| # make sure changed values are there
      assert_equal("value#{i*2}", @server.get("updateval", "key#{i}"))
    end
    
  end
  
  def test_set_nil
    (0..10).each do |i| # set initial values
      @server.put("setnil", Record.new(:key => "key#{i}", :value => "value#{i}"))
    end
    
    (2..6).each do |i| # change some values to nil
      @server.put("setnil", Record.new(:key => "key#{i}", :value => nil))
    end

    (0..1).each do |i| # make sure unchanged values are there
       assert_equal("value#{i}", @server.get("setnil", "key#{i}"))
     end
    (7..10).each do |i| # make sure unchanged values are there
        assert_equal("value#{i}", @server.get("setnil", "key#{i}"))
    end
    (2..6).each do |i| # make sure nil values are there
       assert_nil(@server.get("setnil", "key#{i}"))
    end
  end
    
    def test_set_empty
      (0..10).each do |i| # set initial values
        @server.put("setempty", Record.new(:key => "key#{i}", :value => "value#{i}"))
      end

      (2..6).each do |i| # change some values to empty string
        @server.put("setempty", Record.new(:key => "key#{i}", :value => ""))
      end

      (0..1).each do |i| # make sure unchanged values are there
         assert_equal("value#{i}", @server.get("setempty", "key#{i}"))
      end
      (7..10).each do |i| # make sure unchanged values are there
          assert_equal("value#{i}", @server.get("setempty", "key#{i}"))
      end
      (2..6).each do |i| # make sure empty values are there
         assert_equal("",@server.get("setempty", "key#{i}"))
      end
    end
end





