package com.udacity.webcrawler.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
//import com.fasterxml.jackson.*;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * A static utility class that loads a JSON configuration file.
 */
public final class ConfigurationLoader {

  private final Path path;

  /**
   * Create a {@link ConfigurationLoader} that loads configuration from the given {@link Path}.
   */
  public ConfigurationLoader(Path path) {
    this.path = Objects.requireNonNull(path);
  }

  /**
   * Loads configuration from this {@link ConfigurationLoader}'s path
   *
   * @return the loaded {@link CrawlerConfiguration}.
   */
  public CrawlerConfiguration load() {
    //
    /*
    1) Your load() method will read the JSON string from a file Path which has already been provided
    to the ConfigurationLoader constructor. this.path

    2) Pass that string to the read(Reader reader)

    3) Return the created CrawlerConfiguration. Remember to close the file when you are done!

    */
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      return read(reader);
    } catch (IOException e) {
      System.out.println("Exception reading configuration file");
      e.printStackTrace();
    }


return null;
  }

  /**
   * Loads crawler configuration from the given reader.
   *
   * @param reader a Reader pointing to a JSON string that contains crawler configuration.
   * @return a crawler configuration
   */
  public static CrawlerConfiguration read(Reader reader) {
    // This is here to get rid of the unused variable warning.
    Objects.requireNonNull(reader);

    ObjectMapper objectMapper = new ObjectMapper();
    //Prevent Jackson library from closing the input reader.  This is closed in the #load() method
    objectMapper.disable(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE);

    try {
      CrawlerConfiguration.Builder jsonConfiguredBuilder = objectMapper.readValue(reader, CrawlerConfiguration.Builder.class);
      return jsonConfiguredBuilder.build();
    }catch(Exception e){
      System.out.println("Problem parsing configuration from JSON \n"     );
      e.printStackTrace();
      throw new RuntimeException(e);
    }

  }
}
