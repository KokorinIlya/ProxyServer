# ProxyServer

## Описание 

Программа реализует перенаправление TCP-соединений.

При запуске читает таблицу перенаправлений из config-файла, заданного форматом:

```<local port> <remote address> <remote port>```

Перенаправления соединений с клиента на удалённый сервер в процессе работы программы осуществляют заданное число потоков

## Запуск

```Proxy <путь к config-файлу с таблицей перенаправлений> <число потоков, осуществляющих перенаправление>```

## Прочее

В директориях [клиент](src/ru/ifmo/rain/kokorin/simpleclient/) и [сервер](src/ru/ifmo/rain/kokorin/simpleserver/) находятся примеры тривиальных клиента и сервера, которые можно использовать для проверки прокси-сервера

## Исходный код

Исходный код находится в [директории](src/ru/ifmo/rain/kokorin/proxy/)
