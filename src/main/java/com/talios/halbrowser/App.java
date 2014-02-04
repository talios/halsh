package com.talios.halbrowser;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.theoryinpractise.halbuilder.api.Link;
import com.theoryinpractise.halbuilder.api.ReadableRepresentation;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.standard.StandardRepresentationFactory;
import jline.TerminalFactory;
import jline.console.ConsoleReader;
import jline.console.completer.StringsCompleter;
import jline.console.history.History;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ExecutionException;

/**
 * Hello world!
 */
public class App {

  @AutoValue
  public static abstract class CanonicalRepresentation {
    public static CanonicalRepresentation create(String href, ReadableRepresentation representation) {
      return new AutoValue_App_CanonicalRepresentation(href, representation);
    }

    public abstract String href();

    public abstract ReadableRepresentation representation();
  }

  private static Stack<CanonicalRepresentation> resourceStack = new Stack<CanonicalRepresentation>();

  private static ConsoleReader reader;

  private static final int ATTR_BRIGHT = 1;

  private static final int ATTR_DIM = 2;

  private static final int FG_RED = 31;

  private static final int FG_GREEN = 32;

  private static final int FG_BLUE = 34;

  private static final int FG_MAGENTA = 35;

  private static final int FG_CYAN = 36;

  private static final int FG_WHITE = 37;

  private static final String PREFIX = "\u001b[";

  private static final String SUFFIX = "m";

  private static final char SEPARATOR = ';';

  private static final String END_COLOR = PREFIX + SUFFIX;

  private static String fatalErrColor = PREFIX + ATTR_BRIGHT + SEPARATOR + FG_RED + SUFFIX;

  private static String errColor = PREFIX + ATTR_BRIGHT + SEPARATOR + FG_RED + SUFFIX;

  private static String warnColor = PREFIX + ATTR_BRIGHT + SEPARATOR + FG_MAGENTA + SUFFIX;

  private static String infoColor = PREFIX + ATTR_DIM + SEPARATOR + FG_GREEN + SUFFIX;

  private static String debugColor = PREFIX + ATTR_DIM + SEPARATOR + FG_BLUE + SUFFIX;

  private static String normalColor = PREFIX + SEPARATOR + FG_WHITE + SUFFIX;

  private static boolean isColorized = true;


  private static String contentType = RepresentationFactory.HAL_XML;

  private static String auth = "";

  public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
    if (isColorized) System.out.print(normalColor);

    System.out.println("Welcome to HAL-Browser v1.0.1");
    System.out.println("");

    TerminalFactory.configure(TerminalFactory.AUTO);
    TerminalFactory.reset();

    reader = new ConsoleReader();
    reader.setPrompt("\n> ");
    reader.addCompleter(
        new StringsCompleter("json", "xml", "auth", "open", "follow", "post", "back", "refresh", "quit", "history"));
    reader.addCompleter(new RelsCompleter(resourceStack));
    reader.addCompleter(new UrlsCompleter());

    String input;
    while ((input = reader.readLine().trim()) != null) {

      try {
        reader.getHistory().add(input);
        String[] split = input.split(" ");

        String command = input;
        String commandArg = "";
        if (split.length > 1) {
          command = split[0];
          commandArg = input.substring(command.length() + 1);
        }
        Iterable<String> commandArgs = Splitter.on(newStatefulQuotedStringMatcher()).split(commandArg);

        if ("xml".equals(command)) {
          contentType = RepresentationFactory.HAL_XML;
          refresh();
        } else if ("json".equals(command)) {
          contentType = RepresentationFactory.HAL_JSON;
          refresh();
        } else if ("auth".equals(command)) {
          auth = commandArg;
          refresh();
        } else if ("open".equals(command)) {
          followHref(commandArg);
        } else if ("follow".equals(command)) {

          Iterator<String> iterator = commandArgs.iterator();
          String rel = iterator.next();
          StringBuilder query = new StringBuilder();
          if (iterator.hasNext()) {
            if ((Iterables.size(commandArgs) - 1) % 2 == 0) {
              query.append("?");
              Map<String, String> params = Maps.newHashMap();
              while (iterator.hasNext()) {
                String key = iterator.next();
                String value = iterator.next();
                params.put(key, value);
              }
              query.append(Joiner.on("&").withKeyValueSeparator("=").join(params));
            } else {
              logError("follow params should be even list");
            }
          }

          Optional<Link> link = Optional.fromNullable(resourceStack.peek().representation().getLinkByRel(rel));
          if (link.isPresent()) {
            followHref(link.get().getHref() + query);
          } else {
            logError("ERR: Unknown link with rel " + commandArg);
          }
        } else if ("post".equals(command)) {
          postHref(resourceStack.peek().representation().getResourceLink().getHref(), commandArgs);
        } else if ("back".equals(command)) {
          resourceStack.pop();
          reader.setPrompt("\n" + resourceStack.peek().representation().getResourceLink().getHref() + "> ");
          System.out.println(resourceStack.peek().representation().toString(contentType));
        } else if ("refresh".equals(command)) {
          refresh();
        } else if ("history".equals(command)) {
          for (History.Entry history : reader.getHistory()) {
            System.out.println(String.format("%d - %s", history.index(), history.value()));
          }
        } else if ("quit".equals(command)) {
          System.exit(0);
        } else {
          logError("unknown command.");
        }
      } catch (Exception e) {
        e.printStackTrace();
        logError(e.getMessage());
      }
    }
  }

  private static void refresh() throws IOException, InterruptedException, ExecutionException {
    CanonicalRepresentation resource = resourceStack.pop();
    followHref(resource.href());
  }

  private static void postHref(String href, Iterable<String> params) throws IOException, ExecutionException, InterruptedException {
    if (Iterables.size(params) % 2 == 0) {
      AsyncHttpClient.BoundRequestBuilder post = httpClient.preparePost(href);
      Iterator<String> iterator = params.iterator();
      while (iterator.hasNext()) {
        String key = iterator.next();
        String value = iterator.next();
        post.addParameter(key, value);
      }
      if (auth != null && !"".equals(auth)) {
        post.setHeader("Authorisation", auth);
      }

      Response response = post.execute().get();
      logResponse(response);
      if (response.isRedirected()) {
        followHref(response.getHeader("Location"));
      } else {
        System.out.println(response.getResponseBody());
      }

    } else {
      logError("post params should be even list");
    }
  }

  private static String mkCanonicalHref(String href) throws MalformedURLException {
    if (!href.contains("://")) { // relative href - base it of the current resources URL
      if (!resourceStack.isEmpty()) {
        URL targetURL = new URL(resourceStack.peek().href());
        targetURL = new URL(targetURL, href);
        return targetURL.toExternalForm();
      } else {
        throw new IllegalStateException("Unable to follow relative URL without any existing resource.");
      }
    } else {
      return href;
    }
  }

  private static void followHref(String href) throws IOException, InterruptedException, ExecutionException {

    String targetHref = mkCanonicalHref(href);

    AsyncHttpClient.BoundRequestBuilder get = httpClient.prepareGet(targetHref);
    if (auth != null && !"".equals(auth)) {
      get.setHeader("Authorisation", auth);
    }

    Response response = get.execute().get();
    logResponse(response);
    if (response.getStatusCode() < 400) {
      InputStream stream = response.getResponseBodyAsStream();
      ReadableRepresentation resource = resourceFactory.readRepresentation(new InputStreamReader(stream));

      if (isColorized) System.out.print(infoColor);
      System.out.println(resource.toString(contentType));
      if (isColorized) System.out.print(normalColor);

      reader.setPrompt("\n" + targetHref + "> ");
      resourceStack.push(CanonicalRepresentation.create(targetHref, resource));
    }
  }

  private static void logResponse(Response response) {
    if (isColorized) System.out.print(debugColor);
    System.out.println(String.format("%s %s", response.getStatusCode(), response.getStatusText()));
    for (String headerKey : response.getHeaders().keySet()) {
      System.out.println(String.format("%s: %s", headerKey, response.getHeaders().getJoinedValue(headerKey, ", ")));
    }
    if (isColorized) System.out.print(normalColor);
    System.out.println();
  }

  private static void logError(String error) {
    if (isColorized) System.out.print(errColor);
    System.out.println("Error: " + error);
    if (isColorized) System.out.print(normalColor);
  }

  private static CharMatcher newStatefulQuotedStringMatcher() {
    return CharMatcher.forPredicate(new Predicate<Character>() {
      boolean inQuote = false;

      @Override
      public boolean apply(@Nullable Character input) {
        if ("\"".equals(input.toString())) inQuote = !inQuote;
        return inQuote ? false : CharMatcher.WHITESPACE.apply(input);
      }
    });
  }

  private static AsyncHttpClient httpClient = new AsyncHttpClient();

  private static RepresentationFactory resourceFactory = new StandardRepresentationFactory();

}
