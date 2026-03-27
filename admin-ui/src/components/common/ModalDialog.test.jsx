import { fireEvent, render } from '@testing-library/react'
import ModalDialog from './ModalDialog'

describe('ModalDialog', () => {
  it('closes on Escape when the dialog is not dirty', () => {
    const onClose = vi.fn()

    render(
      <ModalDialog onClose={onClose} title="Example">
        <div>Body</div>
      </ModalDialog>
    )

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('asks for confirmation before closing a dirty dialog on Escape', () => {
    const onClose = vi.fn()
    const confirmSpy = vi.spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)

    render(
      <ModalDialog
        isDirty
        onClose={onClose}
        title="Example"
        unsavedChangesMessage="Discard changes?"
      >
        <div>Body</div>
      </ModalDialog>
    )

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(confirmSpy).toHaveBeenCalledWith('Discard changes?')
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)

    confirmSpy.mockRestore()
  })

  it('only closes the top-most modal on Escape when dialogs are stacked', () => {
    const outerClose = vi.fn()
    const innerClose = vi.fn()

    render(
      <>
        <ModalDialog onClose={outerClose} title="Outer">
          <div>Outer body</div>
        </ModalDialog>
        <ModalDialog onClose={innerClose} title="Inner">
          <div>Inner body</div>
        </ModalDialog>
      </>
    )

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(innerClose).toHaveBeenCalledTimes(1)
    expect(outerClose).not.toHaveBeenCalled()
  })
})
