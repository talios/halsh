package com.talios.halbrowser;

import com.theoryinpractise.halbuilder.api.Link;
import jline.console.completer.Completer;
import jline.console.completer.StringsCompleter;

import java.util.List;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

public class RelsCompleter implements Completer {
  private Stack<App.CanonicalRepresentation> resourceStack;

  public RelsCompleter(Stack<App.CanonicalRepresentation> resourceStack) {
    this.resourceStack = resourceStack;
  }

  public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
    if (buffer.startsWith("follow") && !resourceStack.isEmpty()) {
      SortedSet<String> rels = new TreeSet<String>();
      for (Link link : resourceStack.peek().representation().getLinks()) {
        if (!"self".equals(link.getRel())) {
          rels.add("follow " + link.getRel());
        }
      }

      return new StringsCompleter(rels).complete(buffer, cursor, candidates);
    } else {
      return -1;
    }

  }
}
