import { Design } from '@valenced/discord/design'
import { reloadApp } from '@valenced/modules/native/app'
import type { AnyPlugin } from '@valenced/plugins/_'

const { AlertModal, AlertActionButton, Text } = Design

export default function PluginsRequireReloadAlert({
    plugins,
}: {
    plugins: AnyPlugin[]
}) {
    return (
        <AlertModal
            title="Reload Required"
            content={
                <Text variant="text-md/medium" color="text-default">
                    The following plugins require a reload to apply changes:
                    {'\n'}
                    {plugins.map((plugin, index) => (
                        <>
                            {index ? ', ' : null}
                            <Text
                                key={plugin.manifest.id}
                                variant="text-md/bold"
                                color="text-default"
                            >
                                {plugin.manifest.name}
                            </Text>
                        </>
                    ))}
                </Text>
            }
            actions={
                <>
                    <AlertActionButton
                        variant="destructive"
                        text="Reload"
                        onPress={() => {
                            reloadApp()
                        }}
                    />
                    <AlertActionButton variant="secondary" text="Not now" />
                </>
            }
        />
    )
}
