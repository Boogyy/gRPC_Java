box.cfg{
    listen = 3303
}

box.once("init_kv_schema", function()
    local kv = box.schema.space.create('KV', {
        if_not_exists = true,
        engine = 'vinyl'
    })

    kv:format({
        { name = 'key',   type = 'string' },
        { name = 'value', type = 'varbinary', is_nullable = true }
    })

    kv:create_index('primary', {
        type = 'TREE',
        parts = {
            { field = 'key', type = 'string' }
        },
        if_not_exists = true
    })

    local meta = box.schema.space.create('KV_META', {
        if_not_exists = true,
        engine = 'vinyl'
    })

    meta:format({
        { name = 'name',  type = 'string'   },
        { name = 'value', type = 'unsigned' }
    })

    meta:create_index('primary', {
        type = 'TREE',
        parts = {
            { field = 'name', type = 'string' }
        },
        if_not_exists = true
    })

    if meta:get({ 'count' }) == nil then
        meta:replace({ 'count', 0 })
    end
end)

local function counter_inc()
    box.space.KV_META:update({ 'count' }, {
        { '+', 2, 1 }
    })
end

local function counter_dec()
    box.space.KV_META:update({ 'count' }, {
        { '-', 2, 1 }
    })
end

function kv_put(key, value)
    return box.atomic(function()
        local existed = box.space.KV:get({ key }) ~= nil

        local stored_value = value
        if stored_value == nil then
            stored_value = box.NULL
        end

        local tuple = box.space.KV:replace({ key, stored_value })

        if not existed then
            counter_inc()
        end

        return tuple
    end)
end

function kv_get(key)
    return box.space.KV:get({ key })
end

function kv_delete(key)
    return box.atomic(function()
        local existed = box.space.KV:get({ key }) ~= nil
        if not existed then
            return false
        end

        box.space.KV:delete({ key })

        box.space.KV_META:update({ 'count' }, {
            { '-', 2, 1 }
        })

        return true
    end)
end

function kv_count()
    local tuple = box.space.KV_META:get({ 'count' })
    if tuple == nil then
        return 0
    end
    return tuple[2]
end

function kv_range_page(from_key, to_key, limit, after)
    if from_key == nil or to_key == nil then
        error("from_key and to_key are required")
    end

    if from_key > to_key then
        return { {}, box.NULL }
    end

    local page_limit = tonumber(limit) or 2000
    if page_limit < 1 then
        page_limit = 2000
    end

    local opts = {
        iterator = 'GE',
        limit = page_limit,
        fetch_pos = true
    }

    if after ~= nil and after ~= '' and after ~= box.NULL then
        opts.after = after
    end

    local tuples, next_pos = box.space.KV.index.primary:select({ from_key }, opts)

    if tuples == nil or #tuples == 0 then
        return { {}, box.NULL }
    end

    local items = {}

    for _, tuple in ipairs(tuples) do
        local current_key = tuple[1]

        if current_key > to_key then
            next_pos = nil
            break
        end

        table.insert(items, tuple)
    end

    if #items < #tuples or #items < page_limit then
        next_pos = nil
    end

    if next_pos == nil then
        next_pos = box.NULL
    end

    return { items, next_pos }
end

box.schema.func.create('kv_put',        { if_not_exists = true })
box.schema.func.create('kv_get',        { if_not_exists = true })
box.schema.func.create('kv_delete',     { if_not_exists = true })
box.schema.func.create('kv_count',      { if_not_exists = true })
box.schema.func.create('kv_range_page', { if_not_exists = true })

box.once("init_app_user", function()
    if not box.schema.user.exists('app') then
        box.schema.user.create('app', { password = 'app' })
    end

    box.schema.user.grant('app', 'read,write', 'space', 'KV', { if_not_exists = true })
    box.schema.user.grant('app', 'read,write', 'space', 'KV_META', { if_not_exists = true })

    box.schema.user.grant('app', 'execute', 'function', 'kv_put',        { if_not_exists = true })
    box.schema.user.grant('app', 'execute', 'function', 'kv_get',        { if_not_exists = true })
    box.schema.user.grant('app', 'execute', 'function', 'kv_delete',     { if_not_exists = true })
    box.schema.user.grant('app', 'execute', 'function', 'kv_count',      { if_not_exists = true })
    box.schema.user.grant('app', 'execute', 'function', 'kv_range_page', { if_not_exists = true })
end)








