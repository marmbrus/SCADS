class Word < AvroRecord
  
  #Find word by wordid
  
  def self.createNew(id, word, definition, wordlist)
    w = Word.new
    w.wordid = id
    w.word = word
    w.definition = definition
    w.wordlist = wordlist
    w.save
    w.save #HACK: call everything twice for piql bug
    w
  end

  def self.find(id)
    begin #HACK: rescue exception
      Word.findWord(java.lang.Integer.new(id)) #HACK: call everything twice for piql bug
    rescue Exception => e
      puts "exception was thrown"
      puts e
    end
    w = Word.findWord(java.lang.Integer.new(id))
    puts "***JUST RAN PK QUERY ON WORD***"
    puts w
    return nil if w && w.empty?
    w = w.first unless w == nil || w.empty?
    w = w.first unless w == nil || w.empty?
    w
  end
  
  def self.find_by_word(word)
    w = Word.findWordByWord(word)
    if !w.empty?
        return w.first.first
    else
        return nil
    end
  end
  
  #Returns a random word
  def self.randomWord
    #Pick a random number from the words available
     random = 1 #FIXME
    Word.findWord(java.lang.Integer.new(random)).first.first
  end
  
  #Returns an array of 3 other multiple choice options
  def choices
    #return ["hello", "goodbye", "yay"] #FIXME
    #Randomly pick 3 other words that are not the same as the current word
    #FIXME: 1..20 should be the number of words we have
    
    words = WordList.find(self.wordlist).words
    words = words.shuffle
    words = words.select {|w| w.wordid != self.wordid}
    
    words[0..2].map {|w| w.word}
  end 
  
  def contexts
    begin #HACK: rescue exception
      WordContext.contextsForWord(java.lang.Integer.new(self.wordid))
    rescue Exception => e
      puts "exception was thrown"
      puts e
    end
    wc = WordContext.contextsForWord(java.lang.Integer.new(self.wordid)) #HACK: call everything twice for piql bug
    puts "***JUST CALLED WORD.CONTEXTS***"
    puts wc
    wc = wc.first unless wc == nil || wc.empty?
    wc
  end

  #contexts method only gives one (first) context?  allContexts gives all contexts
  #each result is given as an array, that is:
  #word.allContexts will give
  #   [<#WordContext> <#WordContext> ] etc.
  
  def allContexts
    begin #HACK: rescue exception
      WordContext.contextsForWord(java.lang.Integer.new(self.wordid))
    rescue Exception => e
      puts "exception was thrown"
      puts e
    end
    wc = WordContext.contextsForWord(java.lang.Integer.new(self.wordid)) #HACK: call everything twice for piql bug
    puts "***JUST CALLED WORD.CONTEXTS***"
    puts wc
    wc = wc.map { |c| c.first }
    wc
  end
end
