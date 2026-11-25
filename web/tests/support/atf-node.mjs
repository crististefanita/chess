import nativeAssert from 'node:assert/strict';
import nativeTest from 'node:test';
import { AsyncLocalStorage } from 'node:async_hooks';
import { randomUUID } from 'node:crypto';
import { inspect } from 'node:util';

const active = new AsyncLocalStorage();
const runId = process.env.ATF_NODE_RUN_ID ?? randomUUID();
const show = value => inspect(value, { depth: 12, maxArrayLength: null, maxStringLength: null,
    compact: true, breakLength: Infinity, sorted: true });

function evidence(method, args, passed, observed, location) {
    const owner = active.getStore();
    if (!owner) throw new Error('An audited assertion must belong to its native Node test');
    let expected = args[1];
    let actual = args[0];
    if (['ok', 'assert'].includes(method)) expected = 'truthy';
    if (method.startsWith('not') || method === 'doesNotMatch') expected = 'different from ' + show(expected);
    if (['throws', 'rejects'].includes(method)) {
        expected = args[1] ?? 'an exception';
        actual = observed ? String(observed) : 'no exception';
    }
    if (['doesNotThrow', 'doesNotReject'].includes(method)) {
        expected = 'no exception'; actual = observed ? String(observed) : 'no exception';
    }
    if (method === 'ifError') expected = 'null or undefined';
    const supplied = typeof args.at(-1) === 'string' && args.length >= 3 ? args.at(-1) : undefined;
    const row = { schema: 1, runId, case: owner.name, index: ++owner.assertions,
        name: supplied ?? method + ' #' + owner.assertions, location: location ?? '',
        expected: show(expected), actual: show(actual), passed };
    owner.context.diagnostic('ATF_ASSERT ' + JSON.stringify(row));
}

function operation(method, args) {
    let observed;
    const location = new Error().stack?.split('\n').find(line => line.includes('.test.mjs:'))?.trim();
    const actualArgs = [...args];
    const catches = ['throws', 'rejects', 'doesNotThrow', 'doesNotReject'].includes(method);
    if (catches) {
        const input = args[0];
        if (typeof input === 'function') actualArgs[0] = () => {
            try {
                const value = input();
                return value?.then ? value.catch(error => { observed = error; throw error; }) : value;
            } catch (error) { observed = error; throw error; }
        };
        else if (input?.then) actualArgs[0] = input.catch(error => { observed = error; throw error; });
    }
    try {
        const result = nativeAssert[method](...actualArgs);
        if (result?.then) return result.then(value => { evidence(method, args, true, observed, location); return value; },
            error => { evidence(method, args, false, observed, location); throw error; });
        evidence(method, args, true, observed, location);
        return result;
    } catch (error) { evidence(method, args, false, observed, location); throw error; }
}

export const assert = new Proxy((...args) => operation('ok', args), {
    get(_target, key) {
        if (key === 'strict') return assert;
        const value = nativeAssert[key];
        return typeof value === 'function' ? (...args) => operation(key, args) : value;
    },
});

export function test(name, ...arguments_) {
    const body = arguments_.pop();
    return nativeTest(name, ...arguments_, context => {
        const owner = { name, context, assertions: 0 };
        const wrappedContext = new Proxy(context, { get(target, key) {
            const value = Reflect.get(target, key, target);
            if (typeof value !== 'function') return value;
            if (['after', 'before', 'beforeEach', 'afterEach'].includes(key)) {
                return (hook, ...args) => value.call(target, (...values) => active.run(owner, () => hook(...values)), ...args);
            }
            return value.bind(target);
        } });
        return active.run(owner, () => body(wrappedContext));
    });
}
