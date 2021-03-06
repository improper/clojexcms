-- name: content-all
-- Get Markdown content entities
select * from content order by position asc

-- name: update-content!
-- Set Markdown content body
update content
  set body = :body,
      updated_at = default
  where id = :id
