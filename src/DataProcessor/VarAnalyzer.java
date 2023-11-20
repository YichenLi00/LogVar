import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class VarAnalyzer {
    public static void main(String[] args) {
        String projectName = "activemq-activemq-5.16.0";
        String inputFolderPath = "" + projectName;
        String outputFolderPath = "" + projectName;
        File outputFolder = new File(outputFolderPath);

        if (!outputFolder.exists()) {
            boolean isCreated = outputFolder.mkdirs();
            if (isCreated) {
                System.out.println("Output folder is created successfully");
            } else {
                System.out.println("Failed to create output folder");
            }
        }

        JavaParser javaParser = new JavaParser();
        Pattern PATTERN = Pattern.compile("(?i)(?:log(?:ger)?\\w*)\\s*\\.\\s*(?:log|error|info|warn|fatal|debug|trace|off|all)\\s*\\([^;]*\\);", Pattern.DOTALL);
        Path inputDirPath = Paths.get(inputFolderPath);


        try (Stream<Path> paths = Files.walk(inputDirPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFilePath -> {
                        try {
                            String fileContent = new String(Files.readAllBytes(javaFilePath));
                            Matcher matcher = PATTERN.matcher(fileContent);

                            if (matcher.find()) {
                                ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFilePath);
                                if (parseResult.isSuccessful()) {
                                    CompilationUnit compilationUnit = parseResult.getResult().orElse(null);
                                    if (compilationUnit != null) {
                                        Path relativePath = inputDirPath.relativize(javaFilePath);
                                        String javaFileName = relativePath.toString().replace(".java", "").replace("/", "_");
                                        IdentifierVisitor identifierVisitor = new IdentifierVisitor(outputFolderPath, javaFileName);
                                        identifierVisitor.setJavaFileName(javaFileName); // 设置javaFileName
                                        identifierVisitor.visit(compilationUnit, null);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class IdentifierVisitor extends VoidVisitorAdapter<Void> {
        private String outputFolderPath;
        private String javaFileName;
        private List<Triplet> triplets = new ArrayList<>();

        public IdentifierVisitor(String outputFolderPath, String javaFileName) {
            this.outputFolderPath = outputFolderPath;
            this.javaFileName = javaFileName;
        }

        public void setJavaFileName(String javaFileName) {
            this.javaFileName = javaFileName;
        }

        public void visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            String methodName = methodDeclaration.getNameAsString();
            Set<String> methodVariables = new HashSet<>();
            Optional<MethodCallExpr> firstLogCallOptional = Optional.empty();

            methodDeclaration.findAll(VariableDeclarator.class).forEach(var -> methodVariables.add(var.getNameAsString()));

            // Find the first log call in the method
            List<MethodCallExpr> logCalls = methodDeclaration.findAll(MethodCallExpr.class, this::isLogMethod);
            if (!logCalls.isEmpty()) {
                firstLogCallOptional = Optional.of(logCalls.get(0));
            }

            // If a log statement exists
            if (firstLogCallOptional.isPresent()) {
                MethodCallExpr firstLogCall = firstLogCallOptional.get();
                Set<String> logVariables = new HashSet<>();

                // Find all variables in the log statement
                firstLogCall.findAll(NameExpr.class).forEach(nameExpr -> logVariables.add(nameExpr.getNameAsString()));

                // Add a new triplet with the method code, method variables, and log variables
                Triplet triplet = new Triplet(javaFileName, methodDeclaration.toString(), methodVariables, logVariables);
                triplets.add(triplet);
                Gson gson = new Gson();
                // 将Triplet对象转换为JSON格式
                String json = gson.toJson(triplet);
                Path outputFile = Paths.get(outputFolderPath, javaFileName+"_"+methodName + ".json");
                // 将JSON字符串写入文件
                try {
                        //FileWriter writer = new FileWriter(outputFile)) {
                    //writer.write(json);
                        Files.write(outputFile, json.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean isLogMethod(MethodCallExpr methodCallExpr) {
            String methodCall = methodCallExpr.toString();
            return methodCall.matches("(?i)(?:log(?:ger)?\\w*)\\s*\\.\\s*(?:log|error|info|warn|fatal|debug|trace|off|all)\\s*\\([^;]*\\);");
        }

        public List<Triplet> getTriplets() {
            return triplets;
        }
    }

    private static class Triplet {
        String javaFileName;
        String methodCode;
        Set<String> methodVariables;
        Set<String> logVariables;

        public Triplet(String javaFileName, String methodCode, Set<String> methodVariables, Set<String> logVariables) {
            this.javaFileName = javaFileName;
            this.methodCode = methodCode;
            this.methodVariables = methodVariables;
            this.logVariables = logVariables;
        }
    }
}
