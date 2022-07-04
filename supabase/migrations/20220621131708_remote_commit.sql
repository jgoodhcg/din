CREATE TABLE IF NOT EXISTS public.roam_sync
(
    user__id uuid NOT NULL,
    roam__graph_id text COLLATE pg_catalog."default" NOT NULL,
    roam_sync__latest timestamp with time zone NOT NULL,
    CONSTRAINT roam_sync_pkey PRIMARY KEY (user__id, roam__graph_id),
    CONSTRAINT roam_sync_user__id_fkey FOREIGN KEY (user__id)
        REFERENCES auth.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.roam_sync
    OWNER to supabase_admin;

ALTER TABLE IF EXISTS public.roam_sync
    ENABLE ROW LEVEL SECURITY;

GRANT ALL ON TABLE public.roam_sync TO anon;

GRANT ALL ON TABLE public.roam_sync TO postgres;

GRANT ALL ON TABLE public.roam_sync TO supabase_admin;

GRANT ALL ON TABLE public.roam_sync TO authenticated;

GRANT ALL ON TABLE public.roam_sync TO service_role;
CREATE POLICY "Can insert own user data"
    ON public.roam_sync
    AS PERMISSIVE
    FOR INSERT
    TO public
    WITH CHECK ((auth.uid() = user__id));
CREATE POLICY "Can update own user data"
    ON public.roam_sync
    AS PERMISSIVE
    FOR UPDATE
    TO public
    USING ((auth.uid() = user__id));
CREATE POLICY "Can view own user data"
    ON public.roam_sync
    AS PERMISSIVE
    FOR SELECT
    TO public
    USING ((auth.uid() = user__id));

CREATE TABLE IF NOT EXISTS public.roam_pages
(
    user__id uuid NOT NULL,
    roam__graph_id text COLLATE pg_catalog."default" NOT NULL,
    roam_pages__block_uid text COLLATE pg_catalog."default" NOT NULL,
    roam_pages__node_title text COLLATE pg_catalog."default" NOT NULL,
    roam_pages__edit_time timestamp with time zone NOT NULL,
    CONSTRAINT roam_pages_pkey PRIMARY KEY (user__id, roam__graph_id, roam_pages__block_uid),
    CONSTRAINT roam_pages_user__id_fkey FOREIGN KEY (user__id)
        REFERENCES auth.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.roam_pages
    OWNER to supabase_admin;

ALTER TABLE IF EXISTS public.roam_pages
    ENABLE ROW LEVEL SECURITY;

GRANT ALL ON TABLE public.roam_pages TO anon;

GRANT ALL ON TABLE public.roam_pages TO postgres;

GRANT ALL ON TABLE public.roam_pages TO supabase_admin;

GRANT ALL ON TABLE public.roam_pages TO authenticated;

GRANT ALL ON TABLE public.roam_pages TO service_role;
CREATE POLICY "Can insert own user data"
    ON public.roam_pages
    AS PERMISSIVE
    FOR INSERT
    TO public
    WITH CHECK ((auth.uid() = user__id));
CREATE POLICY "Can update own user data"
    ON public.roam_pages
    AS PERMISSIVE
    FOR UPDATE
    TO public
    USING ((auth.uid() = user__id));
CREATE POLICY "Can view own user data"
    ON public.roam_pages
    AS PERMISSIVE
    FOR SELECT
    TO public
    USING ((auth.uid() = user__id));
