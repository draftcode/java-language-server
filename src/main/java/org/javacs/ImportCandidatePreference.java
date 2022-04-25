package org.javacs;

import static java.util.stream.Collectors.toList;

import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.javacs.proto.ConfigOuterClass.Config;
import org.javacs.proto.ConfigOuterClass.Config.ImportCandidateRule;

public class ImportCandidatePreference {
  private final List<ParsedRule> rules;

  public ImportCandidatePreference(Path workspaceRoot) {
    this.rules = new ArrayList<ParsedRule>();
    Path p = workspaceRoot.toAbsolutePath();
    while (p != null) {
      Path config = p.resolve(".jlsrc");
      if (Files.exists(config)) {
        readConfig(config)
            .getImportCandidateRulesList()
            .forEach(rule -> rules.add(new ParsedRule(rule)));
      }
      p = p.getParent();
    }
  }

  public Resolver getResolver(String simpleName) {
    return new Resolver(
        rules.stream()
            .filter(rule -> rule.missingIdentifierPattern.test(simpleName))
            .collect(toList()));
  }

  private static Config readConfig(Path p) {
    try {
      String s = Files.readString(p);
      var ret = Config.newBuilder();
      TextFormat.merge(s, ret);
      return ret.build();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class Resolver {
    private final List<ParsedRule> matchedRules;

    private Resolver(List<ParsedRule> matchedRules) {
      this.matchedRules = matchedRules;
    }

    public List<String> getExclusiveCandidates() {
      var ret = new ArrayList<String>();
      matchedRules.forEach(rule -> ret.addAll(rule.exclusiveCandidates));
      return ret;
    }

    public boolean shouldIgnore(String candidate) {
      return matchedRules.stream()
          .anyMatch(rule -> rule.ignoreCandidatePatterns.stream().anyMatch(p -> p.test(candidate)));
    }
  }

  private static class ParsedRule {
    private final Predicate<String> missingIdentifierPattern;
    private final List<Predicate<String>> ignoreCandidatePatterns;
    private final List<String> exclusiveCandidates;

    private ParsedRule(ImportCandidateRule rule) {
      this.missingIdentifierPattern =
          Pattern.compile(rule.getMissingIdentifierPattern()).asPredicate();
      this.ignoreCandidatePatterns =
          rule.getIgnoreCandidatePatternsList().stream()
              .map(s -> Pattern.compile(s).asPredicate())
              .collect(toList());
      this.exclusiveCandidates = rule.getExclusiveCandidatesList();
    }
  }
}
