/*
 * Анимация появления цифр.
 * Применяется к элементам с data-count="N".
 * Доп. атрибуты:
 *   data-decimals — кол-во знаков после запятой (по умолчанию 0)
 *   data-duration — длительность анимации в мс (по умолчанию 1600)
 *   data-prefix   — префикс перед числом (например "≈")
 *   data-suffix   — суффикс после числа (например "M+", "%", " ₽")
 *   data-separator — разделитель тысяч (по умолчанию неразрывный пробел)
 */
(function () {
    function format(value, el) {
        const decimals = parseInt(el.dataset.decimals || '0', 10);
        const prefix = el.dataset.prefix || '';
        const suffix = el.dataset.suffix || '';
        const separator = el.dataset.separator || ' ';
        let body;
        if (decimals > 0) {
            body = value.toFixed(decimals).replace('.', ',');
        } else {
            body = Math.round(value).toString();
            // Разделяем тысячи
            body = body.replace(/\B(?=(\d{3})+(?!\d))/g, separator);
        }
        return prefix + body + suffix;
    }

    function animate(el) {
        const target = parseFloat((el.dataset.count || '0').replace(',', '.'));
        if (isNaN(target)) return;
        const duration = parseInt(el.dataset.duration || '1600', 10);
        const startValue = 0;
        let startTs = null;

        function tick(ts) {
            if (startTs === null) startTs = ts;
            const elapsed = ts - startTs;
            const progress = Math.min(elapsed / duration, 1);
            // easeOutCubic
            const eased = 1 - Math.pow(1 - progress, 3);
            const current = startValue + (target - startValue) * eased;
            el.textContent = format(current, el);
            if (progress < 1) requestAnimationFrame(tick);
            else el.textContent = format(target, el);
        }

        requestAnimationFrame(tick);
    }

    function isInViewport(el) {
        const r = el.getBoundingClientRect();
        return r.top < (window.innerHeight || document.documentElement.clientHeight) && r.bottom > 0;
    }

    function init() {
        const nodes = document.querySelectorAll('[data-count]');
        if (!nodes.length) return;

        // Стартовое значение — 0 в формате
        nodes.forEach((el) => { el.textContent = format(0, el); });

        const trigger = (el) => {
            if (el.dataset.animated) return;
            el.dataset.animated = '1';
            animate(el);
        };

        // Если IntersectionObserver не поддерживается — запускаем сразу
        if (!('IntersectionObserver' in window)) {
            nodes.forEach(trigger);
            return;
        }

        const io = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        trigger(entry.target);
                        io.unobserve(entry.target);
                    }
                });
            },
            { threshold: 0.25, rootMargin: '0px 0px -10% 0px' }
        );

        nodes.forEach((el) => {
            // Уже видим — запускаем синхронно (важно для headless и для UX)
            if (isInViewport(el)) {
                trigger(el);
            } else {
                io.observe(el);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
