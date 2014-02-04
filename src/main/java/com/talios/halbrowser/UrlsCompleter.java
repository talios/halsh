package com.talios.halbrowser;

import com.theoryinpractise.halbuilder.spi.Link;
import com.theoryinpractise.halbuilder.spi.ReadableResource;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.StringsCompleter;

import java.util.List;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

public class UrlsCompleter implements Completer {
    private Stack<ReadableResource> resourceStack;

    public UrlsCompleter(Stack<ReadableResource> resourceStack) {
        this.resourceStack = resourceStack;
    }

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        if (buffer.startsWith("open")) {
            SortedSet<String> rels = new TreeSet<String>();
            rels.add("open http://localhost:4567");
            return new StringsCompleter(rels).complete(buffer, cursor, candidates);
        } else {
            return -1;
        }
    }

}
