# ServerComparison

# Инструкция по CLI
```shell
usage: server-comparison
 -blocking         isBlockArchitecture
 -clients <arg>    test if clientNumber is criteria else value of it(M)
 -delta <arg>      test if sendingDelta is criteria else value of it(D)
 -end <arg>        upperBoundValue
 -nonblocking      isNonBlockArchitecture
 -requests <arg>   number of requests(X)
 -resdir <arg>     result directory, current directory is default
 -size <arg>       test if arraySize is criteria else value of it(N)
 -start <arg>      lowerBoundValue
 -step <arg>       step of criteria value
```

Пример набора аргументов:
```shell
-nonblocking -clients 50 -delta 50 -requests 50 -size test -resdir test -start 250 -end 1750 -step 250
```

Результатом работы будет два файла: description.txt и results.csv в папке, указанной в параметре resdir.
