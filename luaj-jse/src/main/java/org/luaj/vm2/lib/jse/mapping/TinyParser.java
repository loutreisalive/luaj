package org.luaj.vm2.lib.jse.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class TinyParser {
  // intermediary -> class mapping
  private final Map<String, ClassMapping> classMappings;

	// named -> intermdiary
	private final Map<String, String> initialMappings;
	
  private TinyParser(InputStream is) {
		this.classMappings = new HashMap<>();
		this.initialMappings = new HashMap<>();

		if(is == null) return;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      reader.readLine(); // skip csv header?

      String line;
      ClassMapping currentClass = null;

      while ((line = reader.readLine()) != null) {
        if (line.startsWith("c\t")) {
          String[] parts = line.substring("c\t".length()).split("\t");
          String official = parts[0].replace('/', '.');
          String intermediary = parts[1].replace('/', '.');
          String named = parts[2].replace('/', '.');

          ClassMapping classMapping = new ClassMapping(official, intermediary, named);
          this.classMappings.put(intermediary, classMapping);
          currentClass = classMapping;

					this.initialMappings.put(named, intermediary);
        } else if (line.startsWith("\tm\t") && currentClass != null) {
          String[] parts = line.substring("\tm\t".length()).split("\t");
          String descriptor = parts[0];
          String official = parts[1];
          String intermediary = parts[2];
          String named = parts[3];

          currentClass.methods.put(
              intermediary, new MemberMapping(descriptor, official, intermediary, named));
        } else if (line.startsWith("\tf\t") && currentClass != null) {
          String[] parts = line.substring("\tf\t".length()).split("\t");
          String descriptor = parts[0];
          String official = parts[1];
          String intermediary = parts[2];
          String named = parts[3];

          currentClass.fields.put(
              intermediary, new MemberMapping(descriptor, official, intermediary, named));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

	public record ClassMapping(
      String official,
      String intermediary,
      String named,
			// intermediary -> member mapping
      Map<String, MemberMapping> methods,
      Map<String, MemberMapping> fields) {
    public ClassMapping(String official, String intermediary, String named) {
      this(official, intermediary, named, new HashMap<>(), new HashMap<>());
    }
  }

  public record MemberMapping(
      String descriptor, String official, String intermediary, String named) {}


	public static TinyParser create(InputStream is) {
		return new TinyParser(is);
	}

	public Map<String, ClassMapping> classMappings() {
		return classMappings;
	}

	public Map<String, String> initialMappings() {
		return initialMappings;
	}
}
