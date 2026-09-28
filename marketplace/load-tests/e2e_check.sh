#!/usr/bin/env bash
# Сквозной сценарий проверки маркетплейса через HTTP (с CSRF-токенами)
B=http://localhost:8080
# Пароль MySQL для проверок состояния БД: DB_PASSWORD=... bash e2e_check.sh
: "${DB_PASSWORD:?Укажите DB_PASSWORD}"
cd "$(dirname "$0")"; W=e2e; rm -rf "$W"; mkdir -p "$W"
S=$RANDOM
pass=0; fail=0
ok()  { echo "  OK   $1"; pass=$((pass+1)); }
bad() { echo "  FAIL $1"; fail=$((fail+1)); }
check() { if [ "$2" = "$3" ]; then ok "$1 ($2)"; else bad "$1: ожидалось $3, получено $2"; fi; }

# токен из страницы
tok() { curl -s -b "$W/$1" -c "$W/$1" "$B$2" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'; }
login() { # jar user pass
  rm -f "$W/$1"; local t=$(tok $1 /auth)
  curl -s -b "$W/$1" -c "$W/$1" -o /dev/null -w "%{redirect_url}" --data-urlencode "username=$2" --data-urlencode "password=$3" -d "_csrf=$t" $B/auth/login
}
post() { # jar page url data... -> http code
  local jar=$1 page=$2 url=$3; shift 3
  local t=$(tok $jar $page)
  curl -s -b "$W/$jar" -c "$W/$jar" -o "$W/last.html" -w "%{http_code}" -H "Referer: $B$page" -d "_csrf=$t" "$@" "$B$url"
}
get() { curl -s -b "$W/$1" -c "$W/$1" -o "$W/last.html" -w "%{http_code}" "$B$2"; }

echo "== 1. Регистрация покупателя и заявка на роль продавца"
t=$(tok g /auth)
code=$(curl -s -b $W/g -c $W/g -o /dev/null -w "%{redirect_url}" -d "_csrf=$t&username=seller$S&email=seller$S@test.ru&password=secret123&confirmPassword=secret123" $B/auth/register)
[[ "$code" == *success* ]] && ok "регистрация seller$S" || bad "регистрация: $code"
r=$(login s seller$S secret123); [[ "$r" == "$B/" ]] && ok "вход seller$S" || bad "вход: $r"
check "без CSRF-токена POST отклоняется" "$(curl -s -b $W/s -o /dev/null -w '%{http_code}' -d 'shopName=x' $B/profile/seller-request)" 403
check "заявка на продавца" "$(post s /profile /profile/seller-request -d shopName=Shop$S -d description=Vintage)" 302
check "повторная заявка отклоняется" "$(post s /profile /profile/seller-request -d shopName=Shop$S)" 302
get s /profile >/dev/null; grep -q "Заявка на рассмотрении" $W/last.html && ok "статус заявки виден в профиле" || bad "статус заявки"

echo "== 2. Администратор одобряет заявку"
r=$(login a admin admin123); [[ "$r" == "$B/" ]] && ok "вход admin" || bad "вход admin: $r"
get a /admin/dashboard >/dev/null
RID=$(grep -o "/admin/seller-requests/[0-9]*/approve" $W/last.html | tail -1 | grep -o "[0-9]*")
[ -n "$RID" ] && ok "заявка в дашборде (#$RID)" || bad "заявка не найдена"
check "одобрение заявки" "$(post a /admin/dashboard /admin/seller-requests/$RID/approve)" 302

CID=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select min(id) from marketplacedb.categories" 2>/dev/null)
echo "== 3. Продавец добавляет товар -> PENDING"
login s seller$S secret123 >/dev/null
check "кабинет продавца доступен" "$(get s /seller/products)" 200
printf '\x89PNG\r\n\x1a\n0000' > $W/img.png
t=$(tok s /seller/products/new)
code=$(curl -s -b $W/s -c $W/s -o /dev/null -w "%{http_code}" -H "Referer: $B/seller/products/new" \
  -F "_csrf=$t" -F "title=Test jacket $S" -F "description=Description" -F "price=4990" -F "condition=USED" -F "categoryId=$CID" \
  -F "images=@$W/img.png;type=image/png" $B/seller/products/new)
check "создание товара" "$code" 302
check "валидация: пустое название" "$(post s /seller/products/new /seller/products/new -d title= -d price=10 -d condition=NEW -d categoryId=$CID)" 302
get s /seller/products >/dev/null
PID=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select max(id) from marketplacedb.products where fk_seller=(select id from marketplacedb.users where username='seller$S')" 2>/dev/null)
[ -n "$PID" ] && ok "товар #$PID создан" || bad "товар не найден"
check "гость не видит неопубликованный товар" "$(get g /product/$PID)" 404
check "владелец видит свой товар" "$(get s /product/$PID)" 200

echo "== 4. Модерация: отклонение с причиной, исправление, одобрение"
check "отклонение без причины не проходит" "$(post a /moderation /moderation/reject/$PID)" 302
check "отклонение с причиной" "$(post a /moderation /moderation/reject/$PID --data-urlencode 'reason=No tag photo')" 302
get s /seller/products >/dev/null; grep -q "No tag photo" $W/last.html && ok "причина видна продавцу" || bad "причина"
get s /profile?tab=notifications >/dev/null; grep -q "отклонён" $W/last.html && ok "уведомление об отклонении" || bad "уведомление отклонения"
t=$(tok s /seller/products/$PID/edit)
check "исправление товара" "$(curl -s -b $W/s -c $W/s -o /dev/null -w '%{http_code}' -H "Referer: $B/seller/products/$PID/edit" -F "_csrf=$t" -F "title=Test jacket $S" -F price=4500 -F condition=USED -F categoryId=$CID $B/seller/products/$PID/edit)" 302
st=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select status from marketplacedb.products where id=$PID" 2>/dev/null)
check "после исправления снова PENDING" "$st" PENDING
check "одобрение" "$(post a /moderation /moderation/approve/$PID)" 302
check "гость видит опубликованный товар" "$(get g /product/$PID)" 200

echo "== 5. Чужой продавец не может править товар"
login d demo_seller password123 >/dev/null
check "demo_seller: редактирование чужого товара" "$(get d /seller/products/$PID/edit)" 403
check "demo_seller: удаление чужого товара" "$(post d /seller/products /seller/products/$PID/delete)" 403

echo "== 6. Покупатель: корзина, отзыв, заказ"
t=$(tok g /auth)
curl -s -b $W/g -c $W/g -o /dev/null -d "_csrf=$t&username=buyer$S&email=buyer$S@test.ru&password=secret123&confirmPassword=secret123" $B/auth/register
login b buyer$S secret123 >/dev/null
check "добавление в корзину" "$(post b /product/$PID /cart/add/$PID)" 302
get b /cart >/dev/null; grep -q "Test jacket $S" $W/last.html && ok "товар в корзине" || bad "корзина"
check "продавец не может купить свой товар" "$(post s /product/$PID /cart/add/$PID)" 302
check "отзыв с запрещённым словом отклонён" "$(post b /product/$PID /product/$PID/review -d rating=5 --data-urlencode 'text=visit https://spam.ru')" 302
check "отзыв" "$(post b /product/$PID /product/$PID/review -d rating=5 --data-urlencode 'text=Great jacket')" 302
n=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select count(*) from marketplacedb.reviews where fk_product=$PID" 2>/dev/null)
check "в базе ровно один отзыв" "$n" 1
check "оформление с некорректным телефоном" "$(post b /orders/checkout /orders/checkout --data-urlencode 'contactName=Ivan' -d contactPhone=abc --data-urlencode 'deliveryAddress=Vladimir')" 302
check "оформление заказа" "$(post b /orders/checkout /orders/checkout --data-urlencode 'contactName=Ivan Ivanov' --data-urlencode 'contactPhone=+7 900 000-00-00' --data-urlencode 'deliveryAddress=Vladimir, Gorkogo 87')" 302
OID=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select id from marketplacedb.orders o where fk_buyer=(select id from marketplacedb.users where username='buyer$S') order by id desc limit 1" 2>/dev/null)
[ -n "$OID" ] && ok "заказ #$OID создан" || bad "заказ"
get s /profile?tab=notifications >/dev/null; grep -q "Новый заказ №$OID" $W/last.html && ok "продавец получил уведомление о заказе" || bad "уведомление продавцу"

echo "== 7. Жизненный цикл заказа"
check "недопустимый переход NEW->COMPLETED" "$(post s /seller/orders /seller/orders/$OID/status -d status=COMPLETED)" 302
st=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select status from marketplacedb.orders where id=$OID" 2>/dev/null); check "статус остался NEW" "$st" NEW
check "чужой продавец не меняет статус" "$(post d /seller/orders /seller/orders/$OID/status -d status=CONFIRMED)" 403
post s /seller/orders /seller/orders/$OID/status -d status=CONFIRMED >/dev/null
st=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select status from marketplacedb.orders where id=$OID" 2>/dev/null); check "NEW -> CONFIRMED" "$st" CONFIRMED
check "покупатель не может отменить подтверждённый" "$(post b /orders /orders/$OID/cancel)" 302
st=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select status from marketplacedb.orders where id=$OID" 2>/dev/null); check "статус остался CONFIRMED" "$st" CONFIRMED
get b /profile?tab=notifications >/dev/null; grep -q "Подтверждён" $W/last.html && ok "покупатель уведомлён о смене статуса" || bad "уведомление покупателю"

echo "== 8. Предупреждение и блокировка"
BID=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select id from marketplacedb.users where username='buyer$S'" 2>/dev/null)
AT=$(tok a /admin/users)
check "предупреждение" "$(curl -s -b $W/a -o /dev/null -w '%{http_code}' -H "X-CSRF-TOKEN: $AT" -H 'X-Requested-With: fetch' -d type=SPAM -d comment=test $B/admin/users/$BID/warn)" 200
check "блокировка" "$(curl -s -b $W/a -o /dev/null -w '%{http_code}' -H "X-CSRF-TOKEN: $AT" -H 'X-Requested-With: fetch' -X POST $B/admin/users/$BID/block)" 200
r=$(login x buyer$S secret123); [[ "$r" == *error* ]] && ok "заблокированный не может войти" || bad "вход заблокированного: $r"
AID=$(mysql -uroot -p"$DB_PASSWORD" -N -e "select id from marketplacedb.users where username='admin'" 2>/dev/null)
check "администратора заблокировать нельзя" "$(curl -s -b $W/a -o /dev/null -w '%{http_code}' -H "X-CSRF-TOKEN: $AT" -H 'X-Requested-With: fetch' -X POST $B/admin/users/$AID/block)" 403
check "разблокировка" "$(curl -s -b $W/a -o /dev/null -w '%{http_code}' -H "X-CSRF-TOKEN: $AT" -H 'X-Requested-With: fetch' -X POST $B/admin/users/$BID/unblock)" 200
check "смена роли (JSON)" "$(curl -s -b $W/a -o /dev/null -w '%{http_code}' -H "X-CSRF-TOKEN: $AT" -H 'X-Requested-With: fetch' -H 'Content-Type: application/json' -d "{\"userId\":$BID,\"newRole\":\"moderator\"}" $B/admin/users/change-role)" 200
login m buyer$S secret123 >/dev/null
check "новый модератор видит очередь" "$(get m /moderation)" 200
check "модератор видит пользователей" "$(get m /moderation/users)" 200
check "модератор не видит админку" "$(get m /admin/dashboard)" 403

echo "== 9. Отчёт и прочее"
check "CSV-отчёт" "$(get a /admin/report)" 200
head -c 300 $W/last.html | grep -q "Статистический отчёт" && ok "содержимое отчёта" || bad "отчёт"
check "/favorites -> профиль" "$(curl -s -b $W/b -o /dev/null -w '%{redirect_url}' $B/favorites | grep -c 'tab=favorites')" 1
for u in / /catalog /brands /about /authentication /auth "/catalog?search=nike&sort=price_asc"; do check "GET $u" "$(get g "$u")" 200; done

echo; echo "Итого: $pass OK, $fail FAIL"
