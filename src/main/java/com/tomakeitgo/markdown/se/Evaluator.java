package com.tomakeitgo.markdown.se;

import java.util.*;

class Evaluator {

    private static final Symbol DEFINE = new Symbol("___define___");
    private static final Symbol DEFAULT_FUNCTION = new Symbol("___default___");
    private static final Symbol NO_SPACES = new Symbol("___no_spaces___");
    private static final Symbol PAREN = new Symbol("___paren___");

    private final Environment environment = new Environment();

    public Evaluator() {
        environment.init();
    }

    public Symbol eval(Expression expression) {
        Function outer = environment.lookup(DEFAULT_FUNCTION);
        return outer.eval(expression, environment);
    }

    private Symbol evalInner(Expression expression, Environment environment) {
        Iterator<SEPart> items = expression.getItems().iterator();
        if (!items.hasNext()) {
            return new Symbol("");
        }

        SEPart toLookup = items.next();

        Function function;
        if (toLookup instanceof Symbol && environment.exists((Symbol) toLookup)) {
            function = environment.lookup((Symbol) toLookup);
        } else {
            function = environment.lookup(DEFAULT_FUNCTION);
        }

        return function.eval(expression, environment.copy());
    }

    public interface Function {
        Symbol eval(Expression args, Environment environment);
    }

    private class Environment {
        private final Map<Symbol, Function> functions = new HashMap<>();
        private final Map<Symbol, SEPart> symbols = new HashMap<>();

        void init() {
            functions.put(DEFAULT_FUNCTION, new DefaultFunction(" ", 0, "", ""));
            functions.put(PAREN, new DefaultFunction(" ", 1, "(", ")"));
            functions.put(NO_SPACES, new DefaultFunction("", 1, "", ""));
            functions.put(DEFINE, new Function() {
                @Override
                public Symbol eval(Expression args, Environment environment) {
                    List<SEPart> items = args.getItems();

                    if (!(items.get(1) instanceof Symbol)) {
                        throw new IllegalArgumentException();
                    }

                    if (!(items.get(2) instanceof Expression)) {
                        throw new IllegalArgumentException();
                    }

                    Expression parameters = (Expression) items.get(2);
                    List<Symbol> symbols = new LinkedList<>();
                    for (SEPart sePart : parameters.getItems()) {
                        symbols.add((Symbol) sePart);
                    }

                    functions.put((Symbol) items.get(1), new DefinedFunction(symbols, items.get(3)));
                    return new Symbol("");
                }
            });
        }

        public boolean exists(Symbol symbol) {
            return functions.containsKey(symbol);
        }

        public Function lookup(Symbol symbol) {
            return functions.get(symbol);
        }

        public void registerSymbol(Symbol symbol, SEPart target) {
            symbols.put(symbol, target);
        }

        public SEPart lookupSymbol(Symbol symbol) {
            return symbols.getOrDefault(symbol, symbol);
        }

        Environment copy() {
            Environment result = new Environment();
            result.functions.putAll(functions);
            result.symbols.putAll(symbols);
            return result;
        }
    }

    class DefaultFunction implements Function {

        private final String separator;
        private final int skip;
        private final String prefix;
        private final String postfix;

        DefaultFunction(String separator, int skip, String prefix, String postfix) {
            this.separator = separator;
            this.skip = skip;
            this.prefix = prefix;
            this.postfix = postfix;
        }

        @Override
        public Symbol eval(Expression args, Environment environment) {
            List<SEPart> items = args.getItems();
            StringBuilder result = new StringBuilder();
            int skipped = 0;
            result.append(prefix);
            for (Iterator<SEPart> iterator = items.iterator(); iterator.hasNext(); ) {
                SEPart item = iterator.next();
                if (skipped < skip) {
                    skipped++;
                    continue;
                }

                if (item instanceof Symbol) {
                    SEPart found = environment.lookupSymbol((Symbol) item);
                    if (found instanceof Symbol) {
                        result.append(((Symbol) found).getValue());
                    } else {
                        result.append(evalInner((Expression) found, environment.copy()).getValue());
                    }
                } else {
                    result.append(evalInner((Expression) item, environment.copy()).getValue());
                }
                result.append(separator);
            }
            if (items.size() > 1 && items.size() >= skipped) {
                result.setLength(result.length() - separator.length());
            }

            result.append(postfix);
            return new Symbol(result.toString().trim());
        }
    }

    class DefinedFunction implements Function {

        private final List<Symbol> parameters;
        private final SEPart body;

        DefinedFunction(List<Symbol> parameters, SEPart body) {
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public Symbol eval(Expression expression, Environment environment) {
            List<SEPart> parts = expression.getItems();
            for (int i = 0; i < parameters.size() && (i + 1) < parts.size(); i++) {
                if (i == parameters.size() - 1) {
                    Expression tail = new Expression();
                    tail.add(new Symbol(""));
                    for (int j = i + 1; j < parts.size(); j++) {
                        tail.add(parts.get(j));
                    }
                    environment.registerSymbol(parameters.get(i), tail);
                } else {
                    Symbol parameter = parameters.get(i);
                    SEPart sePart = parts.get(i + 1);

                    environment.registerSymbol(parameter, sePart);
                }
            }


            if (body instanceof Symbol) {
                int i = parameters.indexOf(body);
                if (i == -1) {
                    return (Symbol) body;
                }

                return (Symbol) parts.get(i + 1);
            } else {
                StringBuilder goo = new StringBuilder();
                Expression expBody = (Expression) body;
                for (SEPart sePart : expBody.getItems()) {
                    if (sePart instanceof Symbol) {
                        SEPart to = environment.lookupSymbol((Symbol) sePart);
                        if (to instanceof Symbol) {
                            goo.append(((Symbol) to).getValue());
                            goo.append(" ");
                        } else {
                            goo.append(evalInner((Expression) to, environment.copy()).getValue());
                        }
                    } else {
                        goo.append(evalInner((Expression) sePart, environment.copy()).getValue());
                    }
                }
                return new Symbol(goo.toString().trim());
            }
        }
    }
}
